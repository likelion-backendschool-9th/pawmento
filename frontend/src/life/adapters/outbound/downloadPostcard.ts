import type { Postcard } from "../../domain/models";
const escape = (value: string) =>
  value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
export async function downloadPostcard(card: Postcard) {
  let image = "";
  if (card.imageUrl) {
    if (card.imageUrl.startsWith("data:"))
      image = `<img src="${escape(card.imageUrl)}" alt="원본 기록 사진"/>`;
    else {
      const reference =
        card.imageUrl.startsWith("reference:") ||
        card.imageUrl.startsWith("/images/");
      const path = reference
        ? "/images/brand/reference-home.png"
        : card.imageUrl;
      const response = await fetch(path);
      if (!response.ok) throw Error("사진을 읽지 못했어요.");
      const blob = await response.blob();
      const data = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(Error("사진을 읽지 못했어요."));
        reader.readAsDataURL(blob);
      });
      image = reference
        ? `<svg viewBox="47 622 500 267" preserveAspectRatio="xMidYMid slice" role="img" aria-label="원본 기록 사진"><image href="${data}" width="941" height="1672"/></svg>`
        : `<img src="${escape(data)}" alt="원본 기록 사진"/>`;
    }
  }
  const html = `<!doctype html><html lang="ko"><meta charset="utf-8"><title>${escape(card.title)}</title><style>body{margin:0;padding:50px 20px;background:#f4f2e8;font-family:system-ui,sans-serif;color:#3b422f}article{max-width:750px;margin:auto;background:#fffdf5;padding:35px;border:1px solid #e0e3d2;border-radius:20px}img,svg{width:100%;max-height:420px;object-fit:cover;border-radius:12px}h1{color:#526b36}p{white-space:pre-wrap;line-height:1.9}small{display:block;color:#7a826d;margin-top:28px}</style><article>${image}<h1>${escape(card.title)}</h1><p>${escape(card.text)}</p><small>데모 엽서 · 실제 AI 생성 아님 · 원본 기록을 바탕으로 만든 미리보기<br>${escape(card.createdAt.slice(0, 10))} · PawMento</small></article></html>`;
  const url = URL.createObjectURL(
    new Blob([html], { type: "text/html;charset=utf-8" }),
  );
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `PawMento-${card.id}.html`;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
