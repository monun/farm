CREATE TABLE IF NOT EXISTS world(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT);
CREATE UNIQUE INDEX IF NOT EXISTS world_index on world(name);
CREATE TABLE IF NOT EXISTS crops(world_id INTEGER, x INTEGER, y INTEGER, z INTEGER, planted_time INTEGER, deleted INTEGER DEFAULT false, FOREIGN KEY(world_id) REFERENCES world(id));
CREATE UNIQUE INDEX IF NOT EXISTS crops_index on crops(world_id, x, z, y);