from typing import Literal

from pydantic import BaseModel, Field, field_validator

from .models import MAX_STOCK


class CategoryIn(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    updated_at: int

    @field_validator("name")
    @classmethod
    def strip_name(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Ad boş olamaz.")
        return value


class ProductIn(CategoryIn):
    category_id: str | None = None


class ProductOut(BaseModel):
    id: str
    name: str
    category_id: str | None
    server_stock: int
    # False when the server already had a newer version (last write wins).
    applied: bool = True


class MovementIn(BaseModel):
    id: str = Field(min_length=1, max_length=36)
    product_id: str
    type: Literal["IN", "OUT"]
    quantity: int = Field(ge=1, le=MAX_STOCK)
    note: str = Field(default="", max_length=500)
    created_at: int


class MovementResult(BaseModel):
    id: str
    status: Literal["ACCEPTED", "REJECTED"]
    reason: str | None
    server_stock: int
    # True when this ID was already processed and the stored result is returned again.
    replayed: bool = False
