from concurrent.futures import ThreadPoolExecutor

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import sessionmaker

from app.database import Base, make_engine
from app.main import app, get_db


@pytest.fixture
def client(tmp_path):
    # A real SQLite file (not :memory:) so parallel requests use separate connections.
    engine = make_engine(f"sqlite:///{tmp_path / 'test.db'}")
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine, expire_on_commit=False)

    def override():
        with Session() as session:
            yield session

    app.dependency_overrides[get_db] = override
    yield TestClient(app)
    app.dependency_overrides.clear()
    engine.dispose()


def make_product(client, product_id="p1", stock=0):
    assert client.put(f"/products/{product_id}", json={"name": "Vida", "updated_at": 1}).status_code == 200
    if stock:
        assert move(client, f"seed-{product_id}", "IN", stock, product_id).json()["status"] == "ACCEPTED"


def move(client, movement_id, type_, quantity, product_id="p1", note=""):
    return client.post("/movements", json={"id": movement_id, "product_id": product_id, "type": type_,
                                           "quantity": quantity, "note": note, "created_at": 1})


def test_entry_and_exit_change_server_stock(client):
    make_product(client, stock=100)
    response = move(client, "m1", "OUT", 20)
    assert response.status_code == 200
    assert response.json() == {"id": "m1", "status": "ACCEPTED", "reason": None,
                               "server_stock": 80, "replayed": False}


def test_same_movement_sent_twice_is_applied_once(client):
    # The phone sent OUT 20, the answer was lost, the phone retries with the SAME id.
    make_product(client, stock=100)
    first = move(client, "m1", "OUT", 20).json()
    second = move(client, "m1", "OUT", 20).json()
    assert first["server_stock"] == 80
    assert second["server_stock"] == 80
    assert second["replayed"] is True


def test_same_id_with_different_content_is_refused(client):
    make_product(client, stock=100)
    move(client, "m1", "OUT", 20)
    assert move(client, "m1", "OUT", 30).status_code == 409
    assert client.get("/products").json()[0]["server_stock"] == 80


def test_insufficient_stock_is_rejected_and_kept(client):
    make_product(client, stock=10)
    result = move(client, "m1", "OUT", 11).json()
    assert result["status"] == "REJECTED"
    assert result["server_stock"] == 10
    # Rejected movement is stored, not silently dropped; a retry gets the same answer.
    assert move(client, "m1", "OUT", 11).json()["status"] == "REJECTED"
    assert client.get("/movements").json()[0]["status"] == "REJECTED"


def test_stock_may_reach_zero(client):
    make_product(client, stock=10)
    assert move(client, "m1", "OUT", 10).json()["server_stock"] == 0


def test_two_devices_cannot_oversell(client):
    # Both phones saw stock 10 while offline and each sold 7. Only one may succeed.
    make_product(client, stock=10)
    assert move(client, "phone-a", "OUT", 7).json()["status"] == "ACCEPTED"
    result = move(client, "phone-b", "OUT", 7).json()
    assert result["status"] == "REJECTED"
    assert result["server_stock"] == 3


def test_parallel_exits_never_make_stock_negative(client):
    make_product(client, stock=10)
    with ThreadPoolExecutor(max_workers=8) as pool:
        results = list(pool.map(lambda i: move(client, f"m{i}", "OUT", 3).json(), range(8)))
    accepted = [r for r in results if r["status"] == "ACCEPTED"]
    assert len(accepted) == 3
    assert client.get("/products").json()[0]["server_stock"] == 1


def test_invalid_quantity_is_refused(client):
    make_product(client, stock=10)
    assert move(client, "m1", "OUT", 0).status_code == 422
    assert move(client, "m2", "OUT", -5).status_code == 422


def test_movement_for_unknown_product_is_refused(client):
    assert move(client, "m1", "IN", 5, product_id="missing").status_code == 404


def test_older_product_edit_does_not_overwrite_newer(client):
    client.put("/products/p1", json={"name": "Yeni ad", "updated_at": 200})
    stale = client.put("/products/p1", json={"name": "Eski ad", "updated_at": 100}).json()
    assert stale["applied"] is False
    assert stale["name"] == "Yeni ad"
