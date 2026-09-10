export default function Avatar({ name, size = 'small' }) {
  const initials = name.trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join('')
  const tone = [...name].reduce((total, letter) => total + letter.charCodeAt(0), 0) % 4

  return <span className={`avatar avatar--${size} avatar--${tone}`} aria-hidden="true">{initials}</span>
}
