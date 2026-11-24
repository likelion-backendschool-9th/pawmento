export function Icon({ name, size = 20 }: { name: string; size?: number }) {
  const paths: Record<string, React.ReactNode> = {
    menu: <path d="M4 6h16M4 12h16M4 18h16" />,
    search: (
      <>
        <circle cx="10" cy="10" r="6" />
        <path d="m15 15 6 6" />
      </>
    ),
    users: (
      <>
        <circle cx="9" cy="8" r="3" />
        <path d="M3 21v-3a6 6 0 0 1 12 0v3m2-16a3 3 0 0 1 0 6m2 3a5 5 0 0 1 3 5v2" />
      </>
    ),
    mail: (
      <>
        <rect x="2" y="4" width="20" height="16" rx="2" />
        <path d="m2 5 10 8L22 5" />
      </>
    ),
    settings: (
      <>
        <path d="m9 3-1 3-3 1-2 5 2 5 3 1 1 3h6l1-3 3-1 2-5-2-5-3-1-1-3Z" />
        <circle cx="12" cy="12" r="3" />
      </>
    ),
    shield: (
      <>
        <path d="M12 2 3 6v6c0 5 9 10 9 10s9-5 9-10V6Z" />
        <path d="m8 12 3 3 5-6" />
      </>
    ),
    bookmark: <path d="M5 3h14v19l-7-5-7 5Z" />,
    comment: <path d="M21 11a9 9 0 0 1-10 9l-7 2 1-7a9 9 0 1 1 16-4Z" />,
    mic: (
      <>
        <rect x="8" y="2" width="8" height="13" rx="4" />
        <path d="M4 10v2a8 8 0 0 0 16 0v-2M12 20v3m-4 0h8" />
      </>
    ),
    video: (
      <>
        <rect x="2" y="5" width="14" height="14" rx="2" />
        <path d="m16 9 6-4v14l-6-4Z" />
      </>
    ),
    sun: (
      <>
        <circle cx="12" cy="12" r="4" />
        <path d="M12 1v3m0 16v3M1 12h3m16 0h3M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2" />
      </>
    ),
    shuffle: (
      <>
        <path d="M3 5h3l12 14h3m-5-4 5 4-5 4M3 19h3L18 5h3m-5-4 5 4-5 4" />
      </>
    ),
    download: (
      <>
        <path d="M12 2v14m-5-5 5 5 5-5M3 16v6h18v-6" />
      </>
    ),
    wifi: (
      <>
        <path d="M2 8a16 16 0 0 1 20 0M5 12a11 11 0 0 1 14 0m-10 4a5 5 0 0 1 6 0" />
        <circle cx="12" cy="20" r="1" />
      </>
    ),
    question: (
      <>
        <circle cx="12" cy="12" r="10" />
        <path d="M9 8a3 3 0 1 1 5 2c-2 1-2 2-2 4m0 3v1" />
      </>
    ),
    home: <path d="m3 10 9-7 9 7v11h-7v-7H9v7H3Z" />,
    book: (
      <path d="M12 5v16M12 5C9 2 5 3 2 4v15c4-1 7-1 10 2 3-3 6-3 10-2V4c-3-1-7-2-10 1Z" />
    ),
    calendar: (
      <>
        <rect x="3" y="5" width="18" height="16" rx="3" />
        <path d="M7 3v4m10-4v4M3 11h18M7 15h2m4 0h2m-8 3h2" />
      </>
    ),
    plus: <path d="M12 5v14M5 12h14" />,
    lock: (
      <>
        <rect x="5" y="11" width="14" height="9" rx="2" />
        <path d="M8 11V8a4 4 0 0 1 8 0v3" />
      </>
    ),
    pin: (
      <>
        <path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11Z" />
        <circle cx="12" cy="10" r="2.4" />
      </>
    ),
    map: (
      <>
        <path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2Z" />
        <path d="M9 4v14m6-12v14" />
      </>
    ),
    route: (
      <>
        <circle cx="6" cy="18" r="2.2" />
        <circle cx="18" cy="6" r="2.2" />
        <path d="M8.2 18H15a3.5 3.5 0 0 0 0-7H9a3.5 3.5 0 0 1 0-7h6.8" />
      </>
    ),
    clock: (
      <>
        <circle cx="12" cy="12" r="8.5" />
        <path d="M12 7.5V12l3 2" />
      </>
    ),
    phone: (
      <>
        <rect x="7" y="2.5" width="10" height="19" rx="2.4" />
        <path d="M11 18.5h2" />
      </>
    ),
    stop: <rect x="6" y="6" width="12" height="12" rx="3" />,
    arrow: <path d="M4 12h16m-6-6 6 6-6 6" />,
    chevron: <path d="m9 5 7 7-7 7" />,
    down: <path d="m6 9 6 6 6-6" />,
    user: (
      <>
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21v-2a8 8 0 0 1 16 0v2" />
      </>
    ),
    leaf: (
      <>
        <path d="M20 3C9 1 2 7 5 15c4 9 17 3 15-12Z" />
        <path d="m3 22 12-14" />
      </>
    ),
    camera: (
      <>
        <path d="m8 5 2-2h4l2 2h4a2 2 0 0 1 2 2v12H2V7a2 2 0 0 1 2-2Z" />
        <circle cx="12" cy="12" r="4" />
      </>
    ),
    edit: (
      <>
        <path d="m15 4 5 5M4 20l5-1L21 7l-4-4L5 15Z" />
      </>
    ),
    close: <path d="m6 6 12 12M6 18 18 6" />,
    check: <path d="m5 12 4 4L19 6" />,
    heart: (
      <path d="M20.8 5.6c-2.3-2.4-6-1.7-8.8 1.3C9.2 3.9 5.5 3.2 3.2 5.6-.7 9.8 5.7 16.1 12 21c6.3-4.9 12.7-11.2 8.8-15.4Z" />
    ),
    paw: (
      <>
        <ellipse cx="6" cy="7" rx="2" ry="3" />
        <ellipse cx="12" cy="5" rx="2" ry="3" />
        <ellipse cx="18" cy="7" rx="2" ry="3" />
        <path d="M6 17c0-3 3-7 6-7s6 4 6 7-4 2-6 2-6 1-6-2Z" />
      </>
    ),
    logout: (
      <>
        <path d="M9 4H4v16h5m5-13 5 5-5 5M8 12h13" />
      </>
    ),
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.65"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name] ?? paths.leaf}
    </svg>
  );
}
