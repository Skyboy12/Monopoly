export default function Logo({ small = false }) {
  return (
    <div className={`brand ${small ? 'brand-small' : ''}`}>
      <span className="brand-dice">◆</span>
      <span>MONOPOLY</span>
      <b>PARTY</b>
    </div>
  )
}
