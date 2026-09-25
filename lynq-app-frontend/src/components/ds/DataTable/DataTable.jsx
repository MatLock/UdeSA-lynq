const cellClassName = (column) => (column.code ? "ds-cell-code" : undefined);

const DataTable = ({ columns, rows, rowKey, caption }) => {
  if (!columns?.length) return null;

  return (
    <div className="ds-tablewrap">
      <table className="ds-table">
        {caption ? <caption className="ds-takeaway">{caption}</caption> : null}
        <thead>
          <tr>
            {columns.map((column) => (
              <th key={column.key} scope="col">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={rowKey ? rowKey(row, index) : index}>
              {columns.map((column) => (
                <td key={column.key} className={cellClassName(column)}>
                  {column.render ? column.render(row) : row[column.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default DataTable;
