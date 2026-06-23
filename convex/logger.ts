/** Structured server-side logging — never log user content at info level. */

export const logger = {
  error(tag: string, message: string, detail?: unknown): void {
    if (detail !== undefined) {
      console.error(`[${tag}] ${message}`, detail);
    } else {
      console.error(`[${tag}] ${message}`);
    }
  },

  warn(tag: string, message: string, detail?: unknown): void {
    if (detail !== undefined) {
      console.warn(`[${tag}] ${message}`, detail);
    } else {
      console.warn(`[${tag}] ${message}`);
    }
  },
};
