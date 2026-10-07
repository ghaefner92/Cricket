export const messages = {
  en: {
    step: '01 / YOUR COMPANION', title: 'Create your Digital Companion', intro: 'A little travel companion. A journey shaped by you.',
    appearance: 'Choose a character', appearanceHint: 'Pick the look you like. Every character can help with the same goals.',
    characters: { robot: 'Robot', explorer: 'Explorer', creature: 'Creature', naturalist: 'Naturalist' },
    descriptions: { robot: 'A curious little co-pilot.', explorer: 'Always ready for a new path.', creature: 'A gentle little zombie with a curious smile.', naturalist: 'A nature-loving companion with two little dogs.' },
    name: 'Give your companion a name', placeholder: 'e.g. Lumi', hint: 'Up to 24 characters. You can change it later.',
    preview: 'YOUR CO-PILOT', anonymous: 'Your companion', greeting: 'Hi! Where shall we go?',
    speech: name => `Hi, I’m ${name}! Let’s find your next route.`,
    save: 'Save companion', saved: 'Companion saved!', savedBody: name => `${name} is ready. Next, you’ll choose what matters most for your travels this week.`,
    next: 'NEXT: WEEKLY GOALS', nextBody: 'Less CO₂? More physical activity? You’ll give your weekly goals a share of a limited points budget.',
    empty: 'Please give your companion a name.', long: 'Please use no more than 24 characters.', storage: 'Your browser could not save this companion. Please allow local storage and try again.',
    autosaved: 'Draft saved on this device.', language: 'Choose language', selected: 'Selected', edit: 'Edit companion',
  },
  de: {
    step: '01 / DEIN BEGLEITER', title: 'Erstelle deinen digitalen Begleiter', intro: 'Ein kleiner Reisebegleiter. Eine Reise nach deinen Wünschen.',
    appearance: 'Wähle eine Figur', appearanceHint: 'Wähle das Aussehen, das dir gefällt. Alle Figuren können dich bei denselben Zielen unterstützen.',
    characters: { robot: 'Roboter', explorer: 'Entdecker', creature: 'Wesen', naturalist: 'Naturfreundin' },
    descriptions: { robot: 'Ein neugieriger kleiner Co-Pilot.', explorer: 'Immer bereit für einen neuen Weg.', creature: 'Ein freundlicher kleiner Zombie mit einem neugierigen Lächeln.', naturalist: 'Eine naturverbundene Begleiterin mit zwei kleinen Hunden.' },
    name: 'Gib deinem Begleiter einen Namen', placeholder: 'z. B. Lumi', hint: 'Bis zu 24 Zeichen. Du kannst ihn später ändern.',
    preview: 'DEIN CO-PILOT', anonymous: 'Dein Begleiter', greeting: 'Hallo! Wohin geht unsere Reise?',
    speech: name => `Hallo, ich bin ${name}! Lass uns deine nächste Route finden.`,
    save: 'Begleiter speichern', saved: 'Begleiter gespeichert!', savedBody: name => `${name} ist bereit. Als Nächstes wählst du, was dir auf deinen Wegen diese Woche besonders wichtig ist.`,
    next: 'DANACH: WOCHENZIELE', nextBody: 'Weniger CO₂? Mehr Bewegung? Du verteilst ein begrenztes Punktebudget auf deine Wochenziele.',
    empty: 'Bitte gib deinem Begleiter einen Namen.', long: 'Bitte verwende höchstens 24 Zeichen.', storage: 'Dein Browser konnte den Begleiter nicht speichern. Bitte erlaube die lokale Speicherung und versuche es erneut.',
    autosaved: 'Entwurf auf diesem Gerät gespeichert.', language: 'Sprache wählen', selected: 'Ausgewählt', edit: 'Begleiter bearbeiten',
  }
}
