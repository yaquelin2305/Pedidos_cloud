export default function DataTable({ caption, columns, rows, rowKey = 'id' }) {
  return <div className="table-container" tabIndex={0} role="region" aria-label={caption}><table><caption className="sr-only">{caption}</caption><thead><tr>{columns.map((column) => <th key={column.key} scope="col">{column.label}</th>)}</tr></thead><tbody>{rows.map((row) => <tr key={row[rowKey]}>{columns.map((column) => <td key={column.key}>{column.render ? column.render(row) : row[column.key]}</td>)}</tr>)}</tbody></table></div>
}
