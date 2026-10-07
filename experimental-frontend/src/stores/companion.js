export const DRAFT_KEY = 'imiq.experimental.companion-draft.v1'
export const SAVED_KEY = 'imiq.experimental.companion.v1'
const OLD_NAME_KEY = 'imiq.experimental.companion-name'
export function normalizeCompanion(value) {
  return {
    version: 1,
    name: typeof value?.name === 'string' ? [...value.name].slice(0, 24).join('') : '',
    appearance: ['robot', 'explorer', 'creature', 'naturalist'].includes(value?.appearance) ? value.appearance : 'robot',
    palette: 'mint',
  }
}
export function readCompanion(storage, { confirmed = false } = {}) {
  try {
    const raw = confirmed ? storage.getItem(SAVED_KEY) : storage.getItem(DRAFT_KEY) || storage.getItem(SAVED_KEY)
    if (raw) {
      try { return normalizeCompanion(JSON.parse(raw)) } catch { /* Fall back to the previous name. */ }
    }
    return normalizeCompanion({ name: storage.getItem(OLD_NAME_KEY) || '' })
  } catch { return normalizeCompanion(null) }
}
export function writeCompanion(storage, value, confirmed = false) {
  const companion = normalizeCompanion(value)
  storage.setItem(confirmed ? SAVED_KEY : DRAFT_KEY, JSON.stringify(companion))
  return companion
}
