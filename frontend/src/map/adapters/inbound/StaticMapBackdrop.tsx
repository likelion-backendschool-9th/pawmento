import { useId } from "react";
import { LABELS, PARK, RIVER, parkTrees } from "./staticMapShapes";
import styles from "./StaticMapBackdrop.module.css";

const TREES = parkTrees();

// 네이버 지도 키가 없을 때 쓰는 예시 지도의 바탕: 블록과 도로, 공원(나무), 강, 지명.
// 화면 비율이 달라도 가운데가 보이도록 잘라 채운다(slice). 색은 부모의 --map-* 변수를 따라 모드(일상·정원)마다 달라진다.
// labels를 끄면 지명(서울숲 등)을 그리지 않는다. 위치를 고르는 화면은 실제 좌표와 무관한 지명이 보이면 오인할 수 있어 끈다.
export function StaticMapBackdrop({ labels = true }: { labels?: boolean }) {
  // 같은 화면에 바탕이 둘 이상 있어도 패턴 id가 겹치지 않게 한다(콜론은 url() 참조에서 빼 둔다)
  const blocks = `map-blocks-${useId().replace(/:/g, "")}`;
  return (
    <svg
      className={styles.backdrop}
      viewBox="0 0 100 100"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        {/* 도로 사이의 블록. 격자를 조금 기울여 실제 지도처럼 보이게 한다 */}
        <pattern id={blocks} width="19" height="17" patternUnits="userSpaceOnUse" patternTransform="rotate(-13)">
          <rect x="0.75" y="0.7" width="17.5" height="15.6" rx="2" className={styles.block} />
        </pattern>
      </defs>
      {/* 도로·블록은 화면 밖까지 이어진다. 위치 고르기에서 지도를 끌어도 배경이 비지 않는다 */}
      <rect x="-500" y="-500" width="1100" height="1100" className={styles.road} />
      <rect x="-500" y="-500" width="1100" height="1100" fill={`url(#${blocks})`} />

      {/* 큰 길(오른쪽 아래로 뻗는 대로) */}
      <path d="M46 61 L120 96" className={styles.avenueCasing} />
      <path d="M46 61 L120 96" className={styles.avenue} />

      {/* 공원과 나무, 산책길 */}
      <ellipse cx={PARK.cx} cy={PARK.cy} rx={PARK.rx} ry={PARK.ry} className={styles.park} />
      <path d="M-8 46 C 14 44, 34 36, 56 24" className={styles.parkPath} />
      {TREES.map((tree, index) => (
        <circle key={index} cx={tree.x} cy={tree.y} r={tree.r} className={styles.tree} />
      ))}

      {/* 강 */}
      <path d={`${RIVER.top} ${RIVER.bottom}`} className={styles.water} />
      <path d={RIVER.top} className={styles.shore} />

      {labels &&
        LABELS.map((label) => (
          <text key={label.text} x={label.x} y={label.y} className={label.water ? styles.waterLabel : styles.label}>
            {label.text}
          </text>
        ))}
    </svg>
  );
}
