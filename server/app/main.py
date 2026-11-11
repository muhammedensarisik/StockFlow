from collections.abc import Iterator
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException
from sqlalchemy import select, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from .database import Base, SessionLocal, engine
from .models import MAX_STOCK, Category, Movement, Product
from .schemas import CategoryIn, MovementIn, MovementResult, ProductIn, ProductOut

@asynccontextmanager
async def lifespan(_app: FastAPI):
    Base.metadata.create_all(engine)
    yield


app = FastAPI(title="StockFlow API", version="0.2.0", lifespan=lifespan)


def get_db() -> Iterator[Session]:
    with SessionLocal() as session:
        yield session


@app.get("/health")
def health() -> dict:
    # Also names the database in use, so a demo can show it is PostgreSQL or SQLite.
    return {"status": "ok", "database": engine.dialect.name}


@app.put("/categories/{category_id}")
def upsert_category(category_id: str, body: CategoryIn, db: Session = Depends(get_db)) -> dict:
    same_name = db.scalar(select(Category).where(Category.name == body.name))
    if same_name is not None and same_name.id != category_id:
        raise HTTPException(409, "Bu adla başka bir kategori sunucuda var.")
    category = db.get(Category, category_id)
    if category is None:
        db.add(Category(id=category_id, name=body.name, updated_at=body.updated_at))
    elif body.updated_at >= category.updated_at:
        category.name, category.updated_at = body.name, body.updated_at
    db.commit()
    return {"id": category_id}


@app.put("/products/{product_id}", response_model=ProductOut)
def upsert_product(product_id: str, body: ProductIn, db: Session = Depends(get_db)) -> ProductOut:
    if body.category_id is not None and db.get(Category, body.category_id) is None:
        raise HTTPException(404, "Kategori henüz sunucuda yok.")
    product = db.get(Product, product_id)
    applied = True
    if product is None:
        product = Product(id=product_id, name=body.name, category_id=body.category_id,
                          updated_at=body.updated_at, stock=0)
        db.add(product)
    elif body.updated_at >= product.updated_at:
        # Simple last-write-wins for product details. Stock is never set here.
        product.name, product.category_id, product.updated_at = body.name, body.category_id, body.updated_at
    else:
        applied = False
    db.commit()
    return ProductOut(id=product.id, name=product.name, category_id=product.category_id,
                      server_stock=product.stock, applied=applied)


@app.get("/products", response_model=list[ProductOut])
def list_products(db: Session = Depends(get_db)) -> list[ProductOut]:
    products = db.scalars(select(Product).order_by(Product.name)).all()
    return [ProductOut(id=p.id, name=p.name, category_id=p.category_id, server_stock=p.stock) for p in products]


@app.get("/movements")
def list_movements(limit: int = 50, db: Session = Depends(get_db)) -> list[dict]:
    rows = db.scalars(select(Movement).order_by(Movement.received_at.desc()).limit(limit)).all()
    return [{"id": m.id, "product_id": m.product_id, "type": m.type, "quantity": m.quantity,
             "note": m.note, "status": m.status, "reason": m.reason} for m in rows]


@app.post("/movements", response_model=MovementResult)
def apply_movement(body: MovementIn, db: Session = Depends(get_db)) -> MovementResult:
    existing = db.get(Movement, body.id)
    if existing is not None:
        return _replay(existing, body, db)

    if db.get(Product, body.product_id) is None:
        raise HTTPException(404, "Ürün henüz sunucuda yok.")

    # Check and change stock in ONE conditional UPDATE. The database locks the row while it
    # runs, so two devices cannot both pass the check with the same old stock value.
    if body.type == "OUT":
        stmt = (update(Product)
                .where(Product.id == body.product_id, Product.stock >= body.quantity)
                .values(stock=Product.stock - body.quantity))
        reason_if_rejected = "Sunucuda yetersiz stok."
    else:
        stmt = (update(Product)
                .where(Product.id == body.product_id, Product.stock <= MAX_STOCK - body.quantity)
                .values(stock=Product.stock + body.quantity))
        reason_if_rejected = "Stok üst sınırı aşılamaz."
    accepted = db.execute(stmt).rowcount == 1

    movement = Movement(id=body.id, product_id=body.product_id, type=body.type, quantity=body.quantity,
                        note=body.note.strip(), created_at=body.created_at,
                        status="ACCEPTED" if accepted else "REJECTED",
                        reason=None if accepted else reason_if_rejected)
    db.add(movement)
    try:
        db.commit()
    except IntegrityError:
        # The same ID arrived twice at the same moment and the other request won.
        # Rolling back also undoes our stock change, then we answer like a normal retry.
        db.rollback()
        existing = db.get(Movement, body.id)
        if existing is None:
            raise
        return _replay(existing, body, db)
    return _result(movement, db, replayed=False)


def _replay(existing: Movement, body: MovementIn, db: Session) -> MovementResult:
    same = (existing.product_id == body.product_id and existing.type == body.type
            and existing.quantity == body.quantity and existing.note == body.note.strip())
    if not same:
        raise HTTPException(409, "Aynı işlem kimliği farklı içerikle gönderildi.")
    return _result(existing, db, replayed=True)


def _result(movement: Movement, db: Session, replayed: bool) -> MovementResult:
    stock = db.scalar(select(Product.stock).where(Product.id == movement.product_id))
    return MovementResult(id=movement.id, status=movement.status, reason=movement.reason,
                          server_stock=stock, replayed=replayed)
