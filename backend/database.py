import os
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker, DeclarativeBase

# Use explicit absolute path to smartvendor.db regardless of CMD working directory
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "smartvendor.db")
DATABASE_URL = f"sqlite:///{DB_PATH}"

engine = create_engine(
    DATABASE_URL,
    connect_args={
        "check_same_thread": False,
        "timeout": 30
    }
)


@event.listens_for(engine, "connect")
def set_sqlite_pragma(dbapi_connection, connection_record):
    cursor = dbapi_connection.cursor()
    cursor.execute("PRAGMA journal_mode=WAL;")
    cursor.execute("PRAGMA synchronous=NORMAL;")
    cursor.execute("PRAGMA cache_size=-64000;")  # 64MB cache
    cursor.execute("PRAGMA busy_timeout=30000;")  # 30 second lock timeout
    cursor.close()


SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


class Base(DeclarativeBase):
    pass


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def run_migrations():
    """
    Safe idempotent SQLite migrations to ensure new columns and tables exist without dropping data.
    """
    from sqlalchemy import text
    with engine.connect() as conn:
        try:
            res = conn.execute(text("PRAGMA table_info(bills);")).fetchall()
            col_names = [r[1] for r in res]
            if col_names and "transaction_type" not in col_names:
                conn.execute(text("ALTER TABLE bills ADD COLUMN transaction_type VARCHAR NOT NULL DEFAULT 'BILL';"))
                conn.commit()
            if col_names and "transaction_id" not in col_names:
                conn.execute(text("ALTER TABLE bills ADD COLUMN transaction_id VARCHAR;"))
                conn.commit()
        except Exception as e:
            print(f"Migration note for bills table: {e}")

        try:
            res_items = conn.execute(text("PRAGMA table_info(bill_items);")).fetchall()
            item_cols = [r[1] for r in res_items]
            if item_cols and "condition" not in item_cols:
                conn.execute(text("ALTER TABLE bill_items ADD COLUMN condition VARCHAR NOT NULL DEFAULT 'GOOD';"))
                conn.commit()
        except Exception as e:
            print(f"Migration note for bill_items table: {e}")

        # Ensure journal_transactions table exists
        try:
            conn.execute(text("""
                CREATE TABLE IF NOT EXISTS journal_transactions (
                    id VARCHAR PRIMARY KEY,
                    transaction_id VARCHAR NOT NULL UNIQUE,
                    user_id VARCHAR NOT NULL,
                    type VARCHAR NOT NULL,
                    bill_id VARCHAR,
                    product_id VARCHAR,
                    product_name VARCHAR,
                    quantity INTEGER DEFAULT 0,
                    unit_price FLOAT DEFAULT 0.0,
                    total_amount FLOAT DEFAULT 0.0,
                    previous_stock INTEGER,
                    new_stock INTEGER,
                    return_condition VARCHAR NOT NULL DEFAULT 'GOOD',
                    status VARCHAR NOT NULL DEFAULT 'APPLIED',
                    payload_json TEXT,
                    timestamp DATETIME,
                    created_at DATETIME
                );
            """))
            conn.execute(text("CREATE INDEX IF NOT EXISTS ix_journal_tx_user_id ON journal_transactions(user_id);"))
            conn.execute(text("CREATE INDEX IF NOT EXISTS ix_journal_tx_id ON journal_transactions(transaction_id);"))
            conn.execute(text("CREATE INDEX IF NOT EXISTS ix_journal_tx_bill_id ON journal_transactions(bill_id);"))
            conn.execute(text("CREATE INDEX IF NOT EXISTS ix_journal_tx_created ON journal_transactions(user_id, created_at);"))
            conn.commit()
        except Exception as e:
            print(f"Migration note for journal_transactions: {e}")

        # Ensure system_checkpoints table exists
        try:
            conn.execute(text("""
                CREATE TABLE IF NOT EXISTS system_checkpoints (
                    id VARCHAR PRIMARY KEY,
                    user_id VARCHAR NOT NULL,
                    checkpoint_id VARCHAR NOT NULL UNIQUE,
                    checkpoint_type VARCHAR NOT NULL DEFAULT 'AUTO',
                    snapshot_data TEXT NOT NULL,
                    products_count INTEGER NOT NULL DEFAULT 0,
                    bills_count INTEGER NOT NULL DEFAULT 0,
                    last_transaction_id VARCHAR,
                    created_at DATETIME
                );
            """))
            conn.execute(text("CREATE INDEX IF NOT EXISTS ix_checkpoints_user_id ON system_checkpoints(user_id);"))
            conn.execute(text("CREATE INDEX IF NOT EXISTS ix_checkpoints_user_created ON system_checkpoints(user_id, created_at);"))
            conn.commit()
        except Exception as e:
            print(f"Migration note for system_checkpoints: {e}")


# Run migrations automatically on import
try:
    run_migrations()
except Exception:
    pass




