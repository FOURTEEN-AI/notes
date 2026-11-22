import { useEffect, useState, type FormEvent } from 'react';

type Note = { id: number; text: string; createdAt: string };

export function App() {
  const [notes, setNotes] = useState<Note[]>([]);
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/notes')
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json() as Promise<Note[]>;
      })
      .then(setNotes)
      .catch(() => setError('Notizen konnten nicht geladen werden.'));
  }, []);

  async function save(event: FormEvent) {
    event.preventDefault();
    setError(null);
    const response = await fetch('/api/notes', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    });
    if (!response.ok) {
      setError('Notiz konnte nicht gespeichert werden.');
      return;
    }
    const note = (await response.json()) as Note;
    setNotes([note, ...notes]);
    setText('');
  }

  return (
    <main>
      <h1>Notizen</h1>
      <form onSubmit={save}>
        <textarea
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder="Neue Notiz"
          rows={4}
        />
        <button type="submit" disabled={text.trim() === ''}>
          Speichern
        </button>
      </form>
      {error && <p className="error">{error}</p>}
      <ul>
        {notes.map((note) => (
          <li key={note.id}>
            <time dateTime={note.createdAt}>{new Date(note.createdAt).toLocaleString('de-DE')}</time>
            <p>{note.text}</p>
          </li>
        ))}
      </ul>
    </main>
  );
}
