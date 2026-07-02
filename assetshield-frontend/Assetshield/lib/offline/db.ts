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

  async all(): Promise<QueuedUpload[]> {
    const db = await getDb();
    const rows = await db.getAllAsync<Row>(`SELECT * FROM upload_queue ORDER BY created_at ASC`);
    return rows.map(toItem);
  },

  async count(): Promise<number> {
    const db = await getDb();
    const row = await db.getFirstAsync<{ c: number }>(`SELECT COUNT(*) AS c FROM upload_queue`);
    return row?.c ?? 0;
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
