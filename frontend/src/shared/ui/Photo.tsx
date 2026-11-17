"use client";
import Image from "next/image";
import { useId, useState } from "react";
import { Icon } from "./Icon";

// Display only the photo regions of the supplied reference; never bake UI text into the app.
const referencePhotos: Record<string, { viewBox: string; source: string }> = {
  "reference:portrait": {
    viewBox: "45 227 173 158",
    source: "reference-home.png",
  },
  "reference:running": {
    viewBox: "47 622 500 267",
    source: "reference-home.png",
  },
  "reference:sleeping": {
    viewBox: "61 1301 155 97",
    source: "reference-home.png",
  },
  "reference:sunset": {
    viewBox: "61 1430 155 99",
    source: "reference-home.png",
  },
  "/images/bori.jpg": {
    viewBox: "45 227 173 158",
    source: "reference-home.png",
  },
  "/images/afternoon.jpg": {
    viewBox: "47 622 500 267",
    source: "reference-home.png",
  },
  "/images/walk.jpg": {
    viewBox: "61 1301 155 97",
    source: "reference-home.png",
  },
};
export function Photo({
  src,
  alt,
  className = "",
}: {
  src?: string;
  alt: string;
  className?: string;
}) {
  const [failedSource, setFailedSource] = useState<string>();
  const titleId = useId();
  const reference = src ? referencePhotos[src] : undefined;
  return (
    <span className={`photo ${className}`}>
      {reference ? (
        <svg
          viewBox={reference.viewBox}
          preserveAspectRatio="xMidYMid slice"
          role="img"
          aria-labelledby={titleId}
          className="reference-photo"
        >
          <title id={titleId}>{alt}</title>
          <image
            href={`/images/brand/${reference.source}`}
            width="941"
            height="1672"
          />
        </svg>
      ) : src && failedSource !== src ? (
        <Image
          key={src}
          src={src}
          alt={alt}
          fill
          sizes="(max-width:767px) 95vw, 650px"
          unoptimized
          onError={() => setFailedSource(src)}
        />
      ) : (
        <span className="photo-placeholder">
          <Icon name="paw" size={32} />
          <span>
            {src ? "사진을 불러오지 못했어요" : "함께할 이야기를 기다려요"}
          </span>
        </span>
      )}
    </span>
  );
}
