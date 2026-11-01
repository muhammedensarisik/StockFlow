import time

from sqlalchemy import BigInteger, CheckConstraint, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column

from .database import Base

MAX_STOCK = 1_000_000_000


def now_millis() -> int:
    return int(time.time() * 1000)


class Category(Base):
    __tablename__ = "categories"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    name: Mapped[str] = mapped_column(String(100), unique=True)
    updated_at: Mapped[int] = mapped_column(BigInteger)


class Product(Base):
    __tablename__ = "products"
    # Last line of defence: even a buggy query cannot store negative stock.
    __table_args__ = (CheckConstraint(f"stock >= 0 AND stock <= {MAX_STOCK}", name="stock_in_range"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    category_id: Mapped[str | None] = mapped_column(ForeignKey("categories.id"), index=True)
    updated_at: Mapped[int] = mapped_column(BigInteger)
    # Central stock. Only accepted movements change it.
    stock: Mapped[int] = mapped_column(BigInteger, default=0)


class Movement(Base):
    __tablename__ = "stock_movements"

    # The ID is created on the phone. Seeing it twice means "same request again", not a new movement.
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    product_id: Mapped[str] = mapped_column(ForeignKey("products.id"), index=True)
    type: Mapped[str] = mapped_column(String(3))
    quantity: Mapped[int] = mapped_column(BigInteger)
    note: Mapped[str] = mapped_column(String(500))
    created_at: Mapped[int] = mapped_column(BigInteger)
    # ACCEPTED or REJECTED. A rejected movement is kept, never silently deleted.
    status: Mapped[str] = mapped_column(String(8))
    reason: Mapped[str | None] = mapped_column(String(200))
    received_at: Mapped[int] = mapped_column(BigInteger, default=now_millis)
