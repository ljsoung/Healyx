"""
병원 추천 API (POST /api/hospitals/recommend) 응답 시간 측정 및 시각화

  측정 조건:
    - 신체 아이콘 12종 × 외래급(riskLevel=2) / 입원급(riskLevel=4) × 5회 반복
    - 위치: 서울 시청 (37.5665, 126.9780)
    - 인증: 게스트 모드 (토큰 불필요)
  출력:
    - hospital_latency_report.png (같은 디렉터리)
    - 콘솔 측정 테이블
  실행:
    python visualize_hospital_latency.py
  전제:
    Spring Boot 서버가 localhost:8080에서 실행 중이어야 함
"""

import requests
import time
import statistics
import matplotlib
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
import matplotlib.patches as mpatches
import numpy as np
from datetime import datetime

# ── 한글 폰트 ──────────────────────────────────────────────────────────────────
matplotlib.rcParams['font.family'] = 'Malgun Gothic'
matplotlib.rcParams['axes.unicode_minus'] = False

# ── 측정 설정 ──────────────────────────────────────────────────────────────────
BASE_URL      = "http://localhost:8080"
RECOMMEND_URL = f"{BASE_URL}/api/hospitals/recommend"
REPEAT        = 5
LATITUDE      = 37.5665    # 서울 시청
LONGITUDE     = 126.9780

# ── 신체 아이콘별 증상 키워드 (BodyIconService.KEYWORD_MAP 기반) ───────────────
ICON_SYMPTOMS = {
    "HEADACHE":        "두통, 어지러움, 메스꺼움",
    "STOMACHACHE":     "복통, 소화불량, 구역질",
    "TOOTHACHE":       "치통, 잇몸통증, 이 시림",
    "DROPLET":         "출혈, 코피, 멍",
    "BROKEN-BONE":     "골절, 외상, 타박상",
    "EAR":             "귀통증, 이명, 청력저하",
    "SKIN":            "발진, 가려움, 두드러기",
    "HEAD-SIDE-COUGH": "기침, 가래, 호흡곤란",
    "VISIBLE":         "시력저하, 눈충혈, 눈통증",
    "NOSE":            "코막힘, 콧물, 코피",
    "COLD":            "감기, 발열, 오한",
    "DISK":            "허리통증, 디스크 통증, 다리 저림",
}

RISK_LABELS  = ["외래급\n(riskLevel=2)", "입원급\n(riskLevel=4)"]
RISK_LEVELS  = [2, 4]
RISK_COLORS  = ["#3498db", "#e67e22"]

# ── API 호출 함수 ─────────────────────────────────────────────────────────────
def call_api(symptom: str, risk_level: int) -> float:
    """한 번 호출하여 소요 시간(ms)을 반환. 실패 시 -1."""
    payload = {
        "symptom":      symptom,
        "latitude":     LATITUDE,
        "longitude":    LONGITUDE,
        "riskLevel":    risk_level,
        "languageCode": "ko",
    }
    try:
        t0 = time.perf_counter()
        resp = requests.post(RECOMMEND_URL, json=payload, timeout=60)
        elapsed_ms = (time.perf_counter() - t0) * 1000
        if resp.status_code == 200:
            return elapsed_ms
        print(f"  [WARN] HTTP {resp.status_code} — symptom={symptom[:10]}, risk={risk_level}")
        return -1
    except requests.exceptions.RequestException as e:
        print(f"  [ERROR] {e}")
        return -1

# ── 측정 실행 ─────────────────────────────────────────────────────────────────
print("=" * 72)
print(" 병원 추천 API 응답 시간 측정 시작")
print(f" 조건: {len(ICON_SYMPTOMS)}개 아이콘 × {len(RISK_LEVELS)}개 위험도 × {REPEAT}회 반복")
print(f" 총 호출 수: {len(ICON_SYMPTOMS) * len(RISK_LEVELS) * REPEAT}회")
print("=" * 72)

# results[icon][risk_idx] = [ms1, ms2, ...]
results: dict[str, list[list[float]]] = {
    icon: [[], []] for icon in ICON_SYMPTOMS
}

total_calls = len(ICON_SYMPTOMS) * len(RISK_LEVELS) * REPEAT
done = 0

for icon, symptom in ICON_SYMPTOMS.items():
    for r_idx, risk in enumerate(RISK_LEVELS):
        for rep in range(REPEAT):
            ms = call_api(symptom, risk)
            if ms > 0:
                results[icon][r_idx].append(ms)
            done += 1
            print(f"  [{done:>3}/{total_calls}] {icon:<20} risk={risk}  "
                  f"rep={rep+1}  → {ms:>9.1f} ms")

# ── 통계 계산 ─────────────────────────────────────────────────────────────────
def p95(data: list[float]) -> float:
    if not data:
        return 0.0
    return float(np.percentile(data, 95))

avg_matrix = np.zeros((len(ICON_SYMPTOMS), len(RISK_LEVELS)))  # [icon, risk]
p95_matrix = np.zeros((len(ICON_SYMPTOMS), len(RISK_LEVELS)))

icon_list = list(ICON_SYMPTOMS.keys())

for i, icon in enumerate(icon_list):
    for r in range(len(RISK_LEVELS)):
        data = results[icon][r]
        avg_matrix[i][r] = statistics.mean(data) if data else 0
        p95_matrix[i][r] = p95(data)

# ── 콘솔 결과 표 ──────────────────────────────────────────────────────────────
print("\n" + "=" * 72)
print(f"{'아이콘':<22} {'외래급 avg':>10} {'외래급 p95':>10} {'입원급 avg':>10} {'입원급 p95':>10}")
print("-" * 72)

all_avgs = []
for i, icon in enumerate(icon_list):
    a0, a1 = avg_matrix[i][0], avg_matrix[i][1]
    p0, p1 = p95_matrix[i][0], p95_matrix[i][1]
    all_avgs.extend([a0, a1])
    print(f"{icon:<22} {a0:>10.1f} {p0:>10.1f} {a1:>10.1f} {p1:>10.1f}")

overall_avg = statistics.mean([v for v in all_avgs if v > 0])
overall_p95 = p95([v for v in all_avgs if v > 0])
print("-" * 72)
print(f"{'전체 평균':.<22} {overall_avg:>10.1f} ms   (p95: {overall_p95:.1f} ms)")
print("=" * 72)

# ── 시각화 ────────────────────────────────────────────────────────────────────
C_BG        = '#f8f9fa'
C_OUTER     = '#e74c3c'    # 입원급
C_INNER     = '#3498db'    # 외래급
C_GRID      = '#dee2e6'
C_TEXT_DARK = '#2c3e50'
C_TEXT_GRAY = '#7f8c8d'
C_P95_LINE  = '#c0392b'
C_AVG_LINE  = '#27ae60'

fig = plt.figure(figsize=(18, 12), facecolor=C_BG)
fig.suptitle(
    '병원 추천 API (POST /api/hospitals/recommend) 응답 시간 측정 결과\n'
    f'n={REPEAT}회 반복  |  12개 신체 아이콘  |  외래급(riskLevel=2) vs 입원급(riskLevel=4)'
    f'  |  측정 일시: {datetime.now().strftime("%Y-%m-%d %H:%M")}',
    fontsize=13, fontweight='bold', y=0.99, color=C_TEXT_DARK
)

gs = gridspec.GridSpec(
    2, 3,
    figure=fig,
    left=0.05, right=0.97,
    top=0.92,  bottom=0.08,
    hspace=0.50, wspace=0.35,
)

# ─────────────────────────────────────────────────────────────────────────────
# [0, 0:2]  아이콘별 평균 응답 시간 — Grouped Bar Chart
# ─────────────────────────────────────────────────────────────────────────────
ax_main = fig.add_subplot(gs[0, 0:2])
ax_main.set_facecolor(C_BG)

x    = np.arange(len(icon_list))
w    = 0.35
bars_inner = ax_main.bar(x - w/2, avg_matrix[:, 0], width=w,
                         color=C_INNER, alpha=0.85,
                         edgecolor='white', linewidth=1.2, zorder=3,
                         label='외래급 (riskLevel=2)')
bars_outer = ax_main.bar(x + w/2, avg_matrix[:, 1], width=w,
                         color=C_OUTER, alpha=0.85,
                         edgecolor='white', linewidth=1.2, zorder=3,
                         label='입원급 (riskLevel=4)')

# 전체 평균 기준선
ax_main.axhline(y=overall_avg, color=C_AVG_LINE, linewidth=1.8,
                linestyle='--', zorder=2,
                label=f'전체 평균 {overall_avg:.0f} ms')

# 수치 레이블 (막대 위)
for bar in list(bars_inner) + list(bars_outer):
    h = bar.get_height()
    if h > 0:
        ax_main.text(bar.get_x() + bar.get_width() / 2, h + 100,
                     f'{h:,.0f}',
                     ha='center', va='bottom', fontsize=7.5,
                     color=C_TEXT_DARK, fontweight='bold')

ax_main.set_xticks(x)
ax_main.set_xticklabels(
    [ic.replace('-', '-\n') if len(ic) > 10 else ic for ic in icon_list],
    fontsize=8.5
)
ax_main.set_ylabel('평균 응답 시간 (ms)', fontsize=10)
ax_main.set_title('신체 아이콘별 평균 추천 응답 시간', fontsize=12,
                  fontweight='bold', color=C_TEXT_DARK, pad=10)
ax_main.legend(fontsize=9, loc='upper right', framealpha=0.85,
               edgecolor=C_GRID)
ax_main.grid(axis='y', color=C_GRID, linewidth=0.8, zorder=1)
ax_main.spines['top'].set_visible(False)
ax_main.spines['right'].set_visible(False)
ax_main.tick_params(axis='x', length=0)

# ─────────────────────────────────────────────────────────────────────────────
# [0, 2]  전체 요약 지표 카드
# ─────────────────────────────────────────────────────────────────────────────
ax_sum = fig.add_subplot(gs[0, 2])
ax_sum.set_facecolor(C_BG)
ax_sum.axis('off')

# 카드 영역
card_specs = [
    ("전체 평균",         f"{overall_avg:,.1f} ms",  '#3498db'),
    ("전체 p95",          f"{overall_p95:,.1f} ms",  '#e67e22'),
    ("외래급 평균",       f"{np.mean(avg_matrix[:,0]):,.1f} ms", '#2980b9'),
    ("입원급 평균",       f"{np.mean(avg_matrix[:,1]):,.1f} ms", '#d35400'),
    ("총 측정 횟수",      f"{len(ICON_SYMPTOMS)*len(RISK_LEVELS)*REPEAT}회",  '#27ae60'),
    ("SLA 기준 (30 s)",   "30,000 ms",               '#95a5a6'),
]

for idx, (label, value, color) in enumerate(card_specs):
    row, col = divmod(idx, 2)
    x0, y0 = 0.02 + col * 0.50, 0.88 - row * 0.32

    rect = mpatches.FancyBboxPatch(
        (x0, y0 - 0.22), 0.44, 0.24,
        boxstyle='round,pad=0.02',
        transform=ax_sum.transAxes,
        facecolor=color, alpha=0.15,
        edgecolor=color, linewidth=1.5,
        clip_on=False,
    )
    ax_sum.add_patch(rect)
    ax_sum.text(x0 + 0.22, y0 - 0.03, value,
                ha='center', va='center',
                transform=ax_sum.transAxes,
                fontsize=13, fontweight='bold', color=color)
    ax_sum.text(x0 + 0.22, y0 - 0.17, label,
                ha='center', va='center',
                transform=ax_sum.transAxes,
                fontsize=8.5, color=C_TEXT_GRAY)

ax_sum.set_title('측정 요약', fontsize=12, fontweight='bold',
                 color=C_TEXT_DARK, pad=10)

# ─────────────────────────────────────────────────────────────────────────────
# [1, 0:2]  p95 vs 평균 비교 막대 (외래 + 입원 합산 기준, 아이콘별)
# ─────────────────────────────────────────────────────────────────────────────
ax_p95 = fig.add_subplot(gs[1, 0:2])
ax_p95.set_facecolor(C_BG)

combined_avg = (avg_matrix[:, 0] + avg_matrix[:, 1]) / 2
combined_p95 = np.array([
    p95(results[icon][0] + results[icon][1]) for icon in icon_list
])

x2 = np.arange(len(icon_list))
w2 = 0.38

ax_p95.bar(x2 - w2/2, combined_avg, width=w2,
           color=C_AVG_LINE, alpha=0.80, edgecolor='white', linewidth=1.2,
           zorder=3, label='평균 (avg)')
ax_p95.bar(x2 + w2/2, combined_p95, width=w2,
           color=C_P95_LINE, alpha=0.80, edgecolor='white', linewidth=1.2,
           zorder=3, label='p95')

# SLA 30초 기준선
ax_p95.axhline(y=30000, color='#8e44ad', linewidth=1.6, linestyle=':',
               zorder=2, label='SLA 기준 30,000 ms')

for i, (avg_v, p95_v) in enumerate(zip(combined_avg, combined_p95)):
    ax_p95.text(i - w2/2, avg_v + 100, f'{avg_v:,.0f}',
                ha='center', va='bottom', fontsize=7, color=C_TEXT_GRAY)
    ax_p95.text(i + w2/2, p95_v + 100, f'{p95_v:,.0f}',
                ha='center', va='bottom', fontsize=7, color=C_TEXT_GRAY)

ax_p95.set_xticks(x2)
ax_p95.set_xticklabels(
    [ic.replace('-', '-\n') if len(ic) > 10 else ic for ic in icon_list],
    fontsize=8.5
)
ax_p95.set_ylabel('응답 시간 (ms)', fontsize=10)
ax_p95.set_title('아이콘별 평균(avg) vs p95 비교 (외래 + 입원 합산)',
                 fontsize=12, fontweight='bold', color=C_TEXT_DARK, pad=10)
ax_p95.legend(fontsize=9, loc='upper right', framealpha=0.85, edgecolor=C_GRID)
ax_p95.grid(axis='y', color=C_GRID, linewidth=0.8, zorder=1)
ax_p95.spines['top'].set_visible(False)
ax_p95.spines['right'].set_visible(False)
ax_p95.tick_params(axis='x', length=0)

# ─────────────────────────────────────────────────────────────────────────────
# [1, 2]  측정 데이터 상세 표
# ─────────────────────────────────────────────────────────────────────────────
ax_tbl = fig.add_subplot(gs[1, 2])
ax_tbl.set_facecolor(C_BG)
ax_tbl.axis('off')

col_labels = ['아이콘', '외래 avg', '외래 p95', '입원 avg', '입원 p95']
table_data = []
for i, icon in enumerate(icon_list):
    table_data.append([
        icon,
        f'{avg_matrix[i][0]:,.0f}',
        f'{p95_matrix[i][0]:,.0f}',
        f'{avg_matrix[i][1]:,.0f}',
        f'{p95_matrix[i][1]:,.0f}',
    ])

tbl = ax_tbl.table(
    cellText=table_data,
    colLabels=col_labels,
    cellLoc='center',
    loc='center',
    bbox=[0, 0, 1, 1],
)
tbl.auto_set_font_size(False)
tbl.set_fontsize(8)

for (row, col), cell in tbl.get_celld().items():
    cell.set_edgecolor(C_GRID)
    if row == 0:
        cell.set_facecolor('#2c3e50')
        cell.set_text_props(color='white', fontweight='bold')
    elif row % 2 == 0:
        cell.set_facecolor('#ecf0f1')
    else:
        cell.set_facecolor('white')

ax_tbl.set_title('응답 시간 상세 표 (ms)', fontsize=11,
                 fontweight='bold', color=C_TEXT_DARK, pad=8)

# ── 하단 주석 ──────────────────────────────────────────────────────────────────
fig.text(
    0.5, 0.01,
    f'측정 조건: 서울 시청 기준 (37.5665, 126.9780) | 게스트 모드 | {REPEAT}회 평균 '
    f'| 전체 평균 응답 시간: {overall_avg:,.1f} ms  |  p95: {overall_p95:,.1f} ms',
    ha='center', fontsize=8.5, color=C_TEXT_GRAY, style='italic',
)

# ── 저장 ──────────────────────────────────────────────────────────────────────
out_path = 'hospital_latency_report.png'
plt.savefig(out_path, dpi=150, bbox_inches='tight', facecolor=C_BG)
print(f'\n저장 완료: {out_path}')
plt.show()
