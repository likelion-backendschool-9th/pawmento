"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useApp } from "@/app/AppProvider";
import { lifeStyles as s } from "../../shell/components/LifeUi";
import { messageOf } from "../../shell/utils/presentation";
import type { FamilyPet } from "../types/serverFamily";

export function MyFamilyPets() {
  const { dependencies } = useApp();
  const [pets, setPets] = useState<FamilyPet[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    dependencies.family.myPets().then(
      (result) => { if (active) setPets(result); },
      (cause) => { if (active) setError(messageOf(cause)); },
    );
    return () => { active = false; };
  }, [dependencies]);

  if (error) return <p className="error" role="alert">{error}</p>;
  if (!pets?.length) return null;
  return (
    <section className={s.panel}>
      <div className={s.panelHeading}>
        <h2>가족으로 함께하는 친구</h2>
        <span className="badge">{pets.length}</span>
      </div>
      {pets.map((pet) => (
        <div className={s.memberRow} key={pet.id}>
          <span className={s.avatar}>{pet.name.slice(0, 1)}</span>
          <div>
            <strong>{pet.name}</strong>
            <small>#{pet.tag}</small>
          </div>
          <Link className="text-link" href={`/pets/manage/${encodeURIComponent(pet.id)}/family`}>
            가족 보기
          </Link>
        </div>
      ))}
    </section>
  );
}
