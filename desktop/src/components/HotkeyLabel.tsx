import type { CSSProperties } from "react";

const KBD_STYLE: CSSProperties = {
  padding: "2px 8px",
  borderRadius: 6,
  background: "rgba(124, 82, 255, 0.2)",
  color: "var(--accent)",
  fontSize: 12,
  fontWeight: 600,
};

export function HotkeyLabel({
  label,
  style,
}: {
  label: string;
  style?: CSSProperties;
}) {
  return (
    <kbd style={{ ...KBD_STYLE, ...style }}>
      {label}
    </kbd>
  );
}
