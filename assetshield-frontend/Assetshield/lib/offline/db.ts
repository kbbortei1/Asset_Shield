import * as SQLite from 'expo-sqlite';

/**
 * Local SQLite store for the offline capture queue (handoff §5). Each row holds
 * the file URI, the hash+metadata computed AT CAPTURE TIME (never re-hashed),
 * and the target endpoint, so a queued upload can be replayed verbatim.
 */
export type QueueKind = 'asset' | 'damage';

export type QueuedUpload = {
  id: string;
  kind: QueueKind;
  endpoint: string;
  fileUri: string;
  fileName: string;
  mimeType: string;
  /** JSON string of AssetMetadata | DamagePhotoMetadata (includes sha256Hash). */
  metadata: string;
  /** human label for the unsynced list, e.g. the asset description */
  label: string;
  createdAt: string;
  attempts: number;
  lastError?: string | null;
};

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = SQLite.openDatabaseAsync('assetshield.db').then(async (db) => {
      await db.execAsync(`
        PRAGMA journal_mode = WAL;
        CREATE TABLE IF NOT EXISTS upload_queue (
          id TEXT PRIMARY KEY NOT NULL,
          kind TEXT NOT NULL,
          endpoint TEXT NOT NULL,
          file_uri TEXT NOT NULL,
          file_name TEXT NOT NULL,
          mime_type TEXT NOT NULL,
          metadata TEXT NOT NULL,
          label TEXT NOT NULL DEFAULT '',
          created_at TEXT NOT NULL,
          attempts INTEGER NOT NULL DEFAULT 0,
          last_error TEXT
        );
      `);
      return db;
    });
  }
  return dbPromise;
}

type Row = {
  id: string;
  kind: QueueKind;
  endpoint: string;
  file_uri: string;
  file_name: string;
  mime_type: string;
  metadata: string;
  label: string;
  created_at: string;
  attempts: number;
  last_error: string | null;
};

const toItem = (r: Row): QueuedUpload => ({
  id: r.id,
  kind: r.kind,
  endpoint: r.endpoint,
  fileUri: r.file_uri,
  fileName: r.file_name,
  mimeType: r.mime_type,
  metadata: r.metadata,
  label: r.label,
  createdAt: r.created_at,
  attempts: r.attempts,
  lastError: r.last_error,
});

/**
 * A row that has failed this many times with a REAL server rejection (not mere
 * connectivity loss) is dead-lettered: excluded from replay so it can't retry
 * forever, but kept so the user can inspect/retry/discard it.
 */
export const MAX_ATTEMPTS = 5;

export const queueDb = {
  async insert(item: Omit<QueuedUpload, 'attempts' | 'lastError'>): Promise<void> {
    const db = await getDb();
    await db.runAsync(
      `INSERT OR REPLACE INTO upload_queue
        (id, kind, endpoint, file_uri, file_name, mime_type, metadata, label, created_at, attempts, last_error)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL)`,
      item.id,
      item.kind,
      item.endpoint,
      item.fileUri,
      item.fileName,
      item.mimeType,
      item.metadata,
      item.label,
      item.createdAt,
    );
  },

  /** Replayable items only — dead-lettered rows are excluded. */
  async all(): Promise<QueuedUpload[]> {
    const db = await getDb();
    const rows = await db.getAllAsync<Row>(
      `SELECT * FROM upload_queue WHERE attempts < ? ORDER BY created_at ASC`,
      MAX_ATTEMPTS,
    );
    return rows.map(toItem);
  },

  async count(): Promise<number> {
    const db = await getDb();
    const row = await db.getFirstAsync<{ c: number }>(
      `SELECT COUNT(*) AS c FROM upload_queue WHERE attempts < ?`,
      MAX_ATTEMPTS,
    );
    return row?.c ?? 0;
  },

  /** Dead-lettered items (exhausted their retries on real server errors). */
  async dead(): Promise<QueuedUpload[]> {
    const db = await getDb();
    const rows = await db.getAllAsync<Row>(
      `SELECT * FROM upload_queue WHERE attempts >= ? ORDER BY created_at ASC`,
      MAX_ATTEMPTS,
    );
    return rows.map(toItem);
  },

  async deadCount(): Promise<number> {
    const db = await getDb();
    const row = await db.getFirstAsync<{ c: number }>(
      `SELECT COUNT(*) AS c FROM upload_queue WHERE attempts >= ?`,
      MAX_ATTEMPTS,
    );
    return row?.c ?? 0;
  },

  /** Give every dead-lettered item a fresh set of retries. */
  async reviveDead(): Promise<void> {
    const db = await getDb();
    await db.runAsync(`UPDATE upload_queue SET attempts = 0 WHERE attempts >= ?`, MAX_ATTEMPTS);
  },

  async remove(id: string): Promise<void> {
    const db = await getDb();
    await db.runAsync(`DELETE FROM upload_queue WHERE id = ?`, id);
  },

  async markFailure(id: string, error: string): Promise<void> {
    const db = await getDb();
    await db.runAsync(`UPDATE upload_queue SET attempts = attempts + 1, last_error = ? WHERE id = ?`, error, id);
  },
};
