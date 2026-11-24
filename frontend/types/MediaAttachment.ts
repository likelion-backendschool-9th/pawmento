export type MediaAttachment = {
  id: string;
  type: "PHOTO" | "VIDEO" | "VOICE";
  url: string;
  name: string;
  mimeType: string;
};
