/** Test-only D1 meta instrumentation. Exported metrics contain counts/timings only. */
export function measure(db: D1Database) {
  const totals = {rowsRead: 0, rowsWritten: 0, statements: 0, sqliteMs: 0};
  const add = (r: D1Result) => {
    totals.rowsRead += r.meta.rows_read; totals.rowsWritten += r.meta.rows_written;
    totals.statements++; totals.sqliteMs += r.meta.duration;
  };
  const wrap = (statement: D1PreparedStatement): D1PreparedStatement => new Proxy(statement, {
    get(target, key) {
      if (key === "bind") return (...args: any[]) => wrap(target.bind(...args));
      if (key === "first") return async (column?: string) => {
        const result = await target.all<Record<string, unknown>>(); add(result);
        return column ? result.results[0]?.[column] ?? null : result.results[0] ?? null;
      };
      if (key === "run" || key === "all") return async () => { const r = await target[key](); add(r); return r; };
      const value = Reflect.get(target, key); return typeof value === "function" ? value.bind(target) : value;
    }
  });
  return {totals, db: new Proxy(db, {
    get(target, key) {
      if (key === "prepare") return (query: string) => wrap(target.prepare(query));
      if (key === "batch") return async (statements: D1PreparedStatement[]) => {
        const results = await target.batch(statements); results.forEach(add); return results;
      };
      const value = Reflect.get(target, key); return typeof value === "function" ? value.bind(target) : value;
    }
  })};
}
