export type Notice = {
  id: string;
  title: string;
  body: string;
  published: boolean;
  createdAt: string;
};
export type DemoMember = {
  id: string;
  name: string;
  status: "ACTIVE" | "SUSPENDED";
};
