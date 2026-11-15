import type { Postcard } from "../../domain/models";
const escape = (value: string) =>
  value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
export async function downloadPostcard(card: Postcard, petName: string) {
  let image = "";
  if (card.imageUrl) {
    if (card.imageUrl.startsWith("data:"))
      image = `<img src="${escape(card.imageUrl)}" alt="원본 기록 사진"/>`;
    else {
      const reference =
        card.imageUrl.startsWith("reference:") ||
        [
          "/images/bori.jpg",
          "/images/afternoon.jpg",
          "/images/walk.jpg",
        ].includes(card.imageUrl);
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
      const viewBox =
        card.imageUrl === "reference:portrait" ||
        card.imageUrl === "/images/bori.jpg"
          ? "45 227 173 158"
          : card.imageUrl === "reference:sleeping" ||
              card.imageUrl === "/images/walk.jpg"
            ? "61 1301 155 97"
            : card.imageUrl === "reference:sunset"
              ? "61 1430 155 99"
              : "47 622 500 267";
      image = reference
        ? `<svg viewBox="${viewBox}" preserveAspectRatio="xMidYMid slice" role="img" aria-label="원본 기록 사진"><image href="${data}" width="941" height="1672"/></svg>`
        : `<img src="${escape(data)}" alt="원본 기록 사진"/>`;
    }
  }
  const backgroundResponse = await fetch("/images/watercolor-garden.png");
  if (!backgroundResponse.ok) throw Error("엽서 배경을 읽지 못했어요.");
  const backgroundBlob = await backgroundResponse.blob();
  const background = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(Error("엽서 배경을 읽지 못했어요."));
    reader.readAsDataURL(backgroundBlob);
  });
  const fontResponse = await fetch("/fonts/PretendardVariable.woff2");
  if (!fontResponse.ok) throw Error("엽서 글꼴을 읽지 못했어요.");
  const fontBlob = await fontResponse.blob();
  const font = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(Error("엽서 글꼴을 읽지 못했어요."));
    reader.readAsDataURL(fontBlob);
  });
  const licenseResponse = await fetch("/fonts/OFL.txt");
  if (!licenseResponse.ok) throw Error("글꼴 사용권을 읽지 못했어요.");
  const license = await licenseResponse.text();
  const html = `<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escape(card.title)}</title><style>
@font-face{font-family:Postcard;src:url("${font}") format("woff2");font-weight:45 920}*{box-sizing:border-box}body{margin:0;padding:40px 20px;background:#eeeae3;color:#665e58;font-family:Georgia,Postcard,sans-serif}main{max-width:900px;margin:auto}article{background:#fffdf5;border:1px solid #e3dac8;padding:20px;margin:25px 0 45px;box-shadow:3px 5px 0 #e0d7c8,0 12px 25px #70624b20;break-inside:avoid}.art{height:380px;display:grid;place-items:center;padding:40px;background:#f3eee3 url('${background}') center/cover}.mount{width:70%;height:100%;padding:10px 10px 24px;background:#fffdf7;box-shadow:0 4px 16px #665e5833;transform:rotate(2deg)}img,svg{width:100%;height:100%;object-fit:cover}h1{font-size:22px;font-weight:400;margin:18px 8px 5px}small{font-size:11px;color:#948777}.front footer{display:flex;justify-content:space-between;padding:12px 8px}.back{padding:36px;display:grid;grid-template-columns:1.2fr 1fr;gap:32px;min-height:450px;background:linear-gradient(#fffdf5ed,#fffdf5ed),url('${background}') center/cover}.letter p{white-space:pre-wrap;overflow-wrap:anywhere;font-size:16px;line-height:2.1}.letter h2{font-size:20px;font-weight:400}.address{border-left:1px solid #d6cbb8;padding-left:28px;display:flex;flex-direction:column}.postage{display:flex;justify-content:flex-end;align-items:center;height:100px}.stamp{border:5px dotted #fffdf5;outline:1px solid #ded6c6;background:#e0e4d7;padding:15px;font-size:26px;color:#829179;transform:rotate(6deg)}.mark{border:2px double #b1a68e;border-radius:50%;width:95px;height:95px;display:grid;place-content:center;text-align:center;font-size:10px;transform:rotate(-15deg);margin-right:-18px;z-index:1;color:#a1947d}.address p{padding-bottom:12px;border-bottom:1px solid #d8cdb9;font-size:14px;line-height:2}.address small{display:block;margin:26px 0 8px;letter-spacing:2px}.postcode{letter-spacing:5px;font-size:24px;color:#b8ac95}.note{text-align:center;font:12px/1.8 system-ui,sans-serif;color:#8c8175}@media(max-width:600px){body{padding:15px}.art{height:240px;padding:22px}.mount{width:90%}.back{grid-template-columns:1fr;padding:24px;gap:20px}.address{border-left:0;border-top:1px solid #d8cdb9;padding:20px 0 0}.postage{float:right}.letter p{font-size:14px}}@media print{@page{size:A5 landscape;margin:8mm}body{padding:0;background:white}main{max-width:none}article{box-shadow:none;margin:0;min-height:120mm;break-after:page;print-color-adjust:exact;-webkit-print-color-adjust:exact}.art{height:90mm}.back{grid-template-columns:1.2fr 1fr}.note{display:none}}
</style><main><article class="front" aria-label="엽서 앞면"><div class="art">${image ? `<div class="mount">${image}</div>` : ""}</div><h1>${escape(card.title)}</h1><footer><small>a little memory, forever</small><small>${escape(card.createdAt.slice(0, 10))} · PawMento</small></footer></article><article class="back" aria-label="엽서 뒷면"><section class="letter"><small>DEAR, OUR PRECIOUS DAYS</small><h2>${escape(card.title)}</h2><p>${escape(card.text)}</p><small>${escape(card.createdAt.slice(0, 10))} · 함께 남긴 기억에서</small></section><section class="address"><div class="postage"><span class="mark">PAWMENTO<br>MEMORY POST<br>${escape(card.createdAt.slice(0, 10))}</span><span class="stamp">❀</span></div><small>TO.</small><p>${escape(petName)}와 함께한 소중한 날들에게</p><p>&nbsp;</p><small>FROM.</small><p>우리의 기억을 간직하는 나</p><span class="postcode">□ □ □ □ □</span></section></article><p class="note">원본 기록을 바탕으로 구성한 데모 엽서 · 수채화 배경은 공통 장식 이미지<br>인쇄할 때 배경 그래픽을 켜면 종이의 색과 그림이 함께 출력됩니다.</p><details class="note"><summary>포함된 Pretendard 글꼴 사용권</summary><pre style="white-space:pre-wrap;text-align:left">${escape(license)}</pre></details></main></html>`;
  const url = URL.createObjectURL(
    new Blob([html], { type: "text/html;charset=utf-8" }),
  );
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = `PawMento-${card.id}.html`;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
