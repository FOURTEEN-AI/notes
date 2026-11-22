import { useEffect, useState } from 'react';

export type DemoStep = { do: string; expect?: string; selector?: string };

type Rect = { top: number; left: number; width: number; height: number };

// Progress per deployed commit: a reload continues, a newer commit starts from step 1.
const storageKey = (sha: string) => `demo-step:${sha}`;
const closed = -1;

function loadStep(sha: string): number {
  try {
    const stored = localStorage.getItem(storageKey(sha));
    const step = Number(stored ?? 0);
    return Number.isInteger(step) ? step : 0;
  } catch {
    return 0;
  }
}

function saveStep(sha: string, step: number) {
  try {
    localStorage.setItem(storageKey(sha), String(step));
  } catch {
    // Without storage the tour only forgets its progress on reload.
  }
}

function find(selector: string | undefined): Element | null {
  if (!selector) return null;
  try {
    return document.querySelector(selector);
  } catch {
    // An invalid selector is treated like one that matches nothing.
    return null;
  }
}

function sameRect(a: Rect | null, b: Rect | null) {
  return (
    a === b ||
    (a !== null &&
      b !== null &&
      a.top === b.top &&
      a.left === b.left &&
      a.width === b.width &&
      a.height === b.height)
  );
}

/**
 * The guided demo of a preview: one step at a time, with the step's element highlighted. It
 * starts by itself and moves on with "Weiter" or a click on the highlighted element.
 */
export function Tour({
  steps,
  sha,
  onMismatch,
}: {
  steps: DemoStep[];
  sha: string;
  onMismatch: (text: string) => void;
}) {
  const [step, setStepState] = useState(() => loadStep(sha));
  const [rect, setRect] = useState<Rect | null>(null);
  const active = step >= 0 && step < steps.length;
  const current = active ? steps[step] : null;

  function setStep(next: number) {
    const value = next >= steps.length ? closed : next;
    saveStep(sha, value);
    setStepState(value);
  }

  // Follow the element: it may appear, move or vanish with every change of the page.
  useEffect(() => {
    if (!current) {
      setRect(null);
      return;
    }
    let scrolled = false;
    function update() {
      const element = find(current?.selector);
      if (!element) {
        setRect((previous) => (previous === null ? previous : null));
        return;
      }
      if (!scrolled) {
        scrolled = true;
        element.scrollIntoView({ block: 'center', behavior: 'smooth' });
      }
      const box = element.getBoundingClientRect();
      const next = { top: box.top, left: box.left, width: box.width, height: box.height };
      // Only a real change re-renders, otherwise the outline's own update would loop.
      setRect((previous) => (sameRect(previous, next) ? previous : next));
    }
    update();
    const observer = new MutationObserver(update);
    observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      characterData: true,
    });
    window.addEventListener('scroll', update, true);
    window.addEventListener('resize', update);
    return () => {
      observer.disconnect();
      window.removeEventListener('scroll', update, true);
      window.removeEventListener('resize', update);
    };
  }, [current]);

  // A click on the highlighted element moves on, after the page has handled the click.
  useEffect(() => {
    if (!current?.selector) return;
    function onClick(event: MouseEvent) {
      const element = find(current?.selector);
      if (element && event.target instanceof Node && element.contains(event.target)) {
        setTimeout(() => setStep(step + 1));
      }
    }
    document.addEventListener('click', onClick, true);
    return () => document.removeEventListener('click', onClick, true);
  });

  if (steps.length === 0) return null;

  if (!current) {
    return (
      <p>
        <button type="button" onClick={() => setStep(0)}>
          Demo starten ({steps.length} {steps.length === 1 ? 'Schritt' : 'Schritte'})
        </button>
      </p>
    );
  }

  function mismatch() {
    if (!current) return;
    onMismatch(
      `Schritt ${step + 1} passt nicht: ${current.do}\n` +
        (current.expect ? `Erwartet: ${current.expect}\n` : '') +
        'Tatsächlich: ',
    );
  }

  return (
    <>
      {rect && (
        <div
          className="tour-highlight"
          style={{
            top: rect.top - 4,
            left: rect.left - 4,
            width: rect.width + 8,
            height: rect.height + 8,
          }}
        />
      )}
      <section className="tour" aria-live="polite">
        <div className="tour-header">
          <strong>
            Demo {step + 1}/{steps.length}
          </strong>
          <button type="button" className="icon-button" onClick={() => setStep(closed)}>
            Beenden
          </button>
        </div>
        <p>{current.do}</p>
        {current.expect && <p className="tour-expect">Erwartet: {current.expect}</p>}
        <div className="actions">
          <button type="button" onClick={() => setStep(step - 1)} disabled={step === 0}>
            Zurück
          </button>
          <button type="button" onClick={mismatch}>
            Passt nicht
          </button>
          <button type="button" onClick={() => setStep(step + 1)}>
            {step + 1 === steps.length ? 'Fertig' : 'Weiter'}
          </button>
        </div>
      </section>
    </>
  );
}
