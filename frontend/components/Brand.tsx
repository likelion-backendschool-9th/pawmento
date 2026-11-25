import Link from "next/link";
import Image from "next/image";
export function Brand({ href = "/" }: { href?: string }) {
  return (
    <Link href={href} className="brand" aria-label="PawMento 처음으로">
      <span className="brand-logo">
        <Image
          src="/images/4abce15e-8aa3-43d2-97ca-0b3435fecf61.png"
          alt="PawMento · 함께한 기억을 담는 책"
          width={110}
          height={74}
          unoptimized
          loading="eager"
        />
      </span>
      <span className="brand-words">
        Paw<span className="brand-light">Mento</span>
        <small>우리의 오늘을, 오래도록</small>
      </span>
    </Link>
  );
}
