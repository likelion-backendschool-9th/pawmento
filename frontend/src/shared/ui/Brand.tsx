import Link from "next/link";
import Image from "next/image";
export function Brand({
  href = "/",
  garden = false,
}: {
  href?: string;
  garden?: boolean;
}) {
  return (
    <Link href={href} className="brand" aria-label="PawMento 처음으로">
      <span className="brand-logo">
        <Image
          src={
            garden
              ? "/images/8df6278d43d07b9e.png"
              : "/images/04e9e24933b3e4bb.png"
          }
          alt={garden ? "PawMento 정원 로고" : "PawMento 코기 로고"}
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
