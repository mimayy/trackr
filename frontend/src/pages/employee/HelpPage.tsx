import { useState } from 'react'

interface HelpTopic {
  question: string
  answer: string[]
}

/**
 * Help written for the person, not the system.
 *
 * No API names, no error codes, no configuration language - just the six things
 * that actually go wrong, each answered in a couple of short sentences.
 */
const TOPICS: HelpTopic[] = [
  {
    question: 'How do I Time In?',
    answer: [
      'Open Home and tap the big TIME IN button.',
      'Your phone will ask for your location and your fingerprint. Follow the prompt, and you are done.',
      'You only need to Time In once at the start of your workday.',
    ],
  },
  {
    question: 'How do I record Field Arrival?',
    answer: [
      "When you reach the warehouse, open Home, tap I'M HERE and type or tap its name.",
      'Trackr checks that you are at the right place, then records your arrival.',
      'If it says you are not there yet, walk a little closer to the building and try again.',
    ],
  },
  {
    question: "What if I'm offline?",
    answer: [
      'You can keep working normally. Tap the buttons as usual.',
      'Your activity is saved on your phone and marked "Waiting to sync".',
      'As soon as you have a signal again, it is sent automatically. You do not need to do anything.',
    ],
  },
  {
    question: "What if my location can't be found?",
    answer: [
      'Make sure Location Services are turned on for your browser.',
      'Step outside or move away from thick walls, then tap TRY AGAIN.',
      'GPS can take a few seconds indoors or in a covered warehouse.',
    ],
  },
  {
    question: "What if my fingerprint doesn't work?",
    answer: [
      'Wipe your finger and the sensor, then try again.',
      'If it still will not read, you can continue without it — your activity is still recorded with your location and the time.',
      'Your supervisor will simply see that this one was not fingerprint-verified.',
    ],
  },
  {
    question: 'What if I get a new phone?',
    answer: [
      'Tell your administrator. They will reset your device and send you a new activation link.',
      'Open that link on the new phone and set up your fingerprint again.',
      'Your account and all your past attendance stay exactly as they are.',
    ],
  },
  {
    question: 'I forgot to Time Out yesterday',
    answer: [
      'Tell your supervisor. They can see the day is incomplete and have it corrected.',
      'The original record is never deleted — a correction is added on top of it, with the reason.',
    ],
  },
]

export function HelpPage() {
  const [open, setOpen] = useState<number | null>(null)

  return (
    <div className="stack-lg">
      <div>
        <h1>Help</h1>
        <p className="muted" style={{ marginTop: 6 }}>
          Tap a question to see the answer.
        </p>
      </div>

      <div className="stack-sm">
        {TOPICS.map((topic, index) => {
          const isOpen = open === index
          return (
            <div key={topic.question} className="card card-tight">
              <button
                type="button"
                onClick={() => setOpen(isOpen ? null : index)}
                aria-expanded={isOpen}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 'var(--space-3)',
                  width: '100%',
                  minHeight: 'var(--tap-min)',
                  padding: 0,
                  background: 'none',
                  border: 'none',
                  font: 'inherit',
                  fontWeight: 600,
                  textAlign: 'left',
                  cursor: 'pointer',
                  color: 'inherit',
                }}
              >
                <span>{topic.question}</span>
                <span aria-hidden="true" style={{ color: 'var(--brand-700)', fontSize: 'var(--text-lg)' }}>
                  {isOpen ? '▾' : '›'}
                </span>
              </button>

              {isOpen && (
                <div style={{ marginTop: 'var(--space-3)' }}>
                  {topic.answer.map((paragraph) => (
                    <p key={paragraph}>{paragraph}</p>
                  ))}
                </div>
              )}
            </div>
          )
        })}
      </div>

      <div className="card stack">
        <div>
          <div className="strong">Still need help?</div>
          <p className="small muted" style={{ marginTop: 4, marginBottom: 0 }}>
            Call your supervisor, or contact the Uni-Ace office.
          </p>
        </div>
        <a className="btn btn-primary btn-hero" href="tel:+6328888000">
          📞 CONTACT SUPPORT
        </a>
      </div>
    </div>
  )
}
