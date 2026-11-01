import os

from sqlalchemy import create_engine, event
from sqlalchemy.orm import DeclarativeBase, sessionmaker

# PostgreSQL example: postgresql+psycopg://stockflow:secret@localhost:5432/stockflow
# Without DATABASE_URL a local SQLite file is used so the demo runs with no database server.
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./stockflow.db")


class Base(DeclarativeBase):
    pass


def make_engine(url: str):
    if url.startswith("sqlite"):
        engine = create_engine(url, connect_args={"check_same_thread": False, "timeout": 15})

        @event.listens_for(engine, "connect")
        def _enable_foreign_keys(connection, _record):
            connection.execute("PRAGMA foreign_keys = ON")

        return engine
    return create_engine(url, pool_pre_ping=True)


engine = make_engine(DATABASE_URL)
SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)
