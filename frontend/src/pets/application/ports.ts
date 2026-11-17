export type PetSummary = {
  id: string;
  name: string;
};

export interface PetListRepository {
  listMine(): Promise<PetSummary[]>;
}
