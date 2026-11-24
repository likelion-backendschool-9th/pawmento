import { today } from "@/lib/date";
import { speciesName } from "../../shell/utils/presentation";
import type { RegisterPetInput } from "../types/ManagedPet";

export function PetProfileFields({
  input,
  change,
  breedMaxLength,
  disabled = false,
}: {
  input: RegisterPetInput;
  change: <K extends keyof RegisterPetInput>(
    key: K,
    value: RegisterPetInput[K],
  ) => void;
  breedMaxLength: number;
  disabled?: boolean;
}) {
  return (
    <>
      <label>
        이름 <span className="required">필수</span>
        <input
          disabled={disabled}
          value={input.name}
          onChange={(event) => change("name", event.target.value)}
          placeholder="친구의 이름은 무엇인가요?"
          maxLength={20}
          required
        />
      </label>
      <label>
        어떤 친구인가요?
        <select
          disabled={disabled}
          value={input.species}
          onChange={(event) =>
            change("species", event.target.value as RegisterPetInput["species"])
          }
        >
          {Object.entries(speciesName).map(([value, label]) => (
            <option value={value} key={value}>
              {label}
            </option>
          ))}
        </select>
      </label>
      <label>
        품종 <span className="muted">선택</span>
        <input
          list="breed-options"
          disabled={disabled}
          value={input.breed}
          maxLength={breedMaxLength}
          onChange={(event) => change("breed", event.target.value)}
          placeholder="찾거나 직접 입력해 주세요"
        />
        <datalist id="breed-options">
          {(input.species === "DOG"
            ? [
                "웰시코기",
                "말티즈",
                "푸들",
                "포메라니안",
                "골든리트리버",
                "믹스",
              ]
            : input.species === "CAT"
              ? ["코리안숏헤어", "러시안블루", "랙돌", "페르시안", "믹스"]
              : []
          ).map((breed) => (
            <option key={breed} value={breed} />
          ))}
        </datalist>
      </label>
      <label>
        생일 <span className="muted">선택</span>
        <input
          type="date"
          disabled={disabled}
          value={input.birthday}
          max={today()}
          onChange={(event) => change("birthday", event.target.value)}
        />
      </label>
    </>
  );
}
