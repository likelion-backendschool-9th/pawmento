export type Postcard = {
  id: string;
  petId: string;
  ownerId: string;
  title: string;
  text: string;
  imageUrl: string;
  sourceEntries: { kind: "DIARY" | "QUESTION_ANSWER"; id: string }[];
  legacySourceRecordIds?: string[];
  demoArtwork?: {
    scene: "PARK" | "BEACH" | "NAP";
    prompt: string;
    referencePhotoUrl: string;
  };
  status: "PROCESSING" | "COMPLETED" | "FAILED";
  error: string;
  saved: boolean;
  createdAt: string;
};
