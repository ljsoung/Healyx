"""
클린봇 정탐률·오탐률 측정 시각화
  - 측정 조건: n=60, gpt-5.4-mini, temperature=0
  - 출력: cleanbot_accuracy_report.png (같은 디렉터리)
  - 실행: python visualize_cleanbot.py
"""

import matplotlib
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
import matplotlib.patches as mpatches
import numpy as np

# ── 한글 폰트 (Windows: Malgun Gothic / Mac: AppleGothic / Linux: NanumGothic) ──
matplotlib.rcParams['font.family'] = 'Malgun Gothic'
matplotlib.rcParams['axes.unicode_minus'] = False

# ── 측정 수치 ─────────────────────────────────────────────────────────────────
TP, FN, FP, TN = 29, 1, 0, 30
N = TP + FN + FP + TN   # 60

recall    = TP / (TP + FN)                                          # 0.967
fpr       = FP / (FP + TN) if (FP + TN) > 0 else 0.0              # 0.000
precision = TP / (TP + FP) if (TP + FP) > 0 else 1.0              # 1.000
f1        = 2 * precision * recall / (precision + recall)           # 0.983

# ── 카테고리별 수치 ────────────────────────────────────────────────────────────
# (차단 수, 전체 수)
harmful_data = {
    'PROFANITY\n(욕설)':          (5, 5),
    'HATE\n(혐오 발언)':           (5, 5),
    'SPAM\n(스팸)':                (5, 5),
    'MISINFORMATION\n(허위사실)':  (5, 5),
    'BORDERLINE\n(회색지대)':      (9, 10),
}
clean_data = {
    'GENERAL\n(일반 대화)':        (0, 5),
    'HOSPITAL\n(병원 후기)':       (0, 5),
    'MEDICAL\n(의료 질문)':        (0, 5),
    'SMALL TALK\n(일상 소통)':     (0, 5),
    'BORDERLINE\n(회색지대)':      (0, 10),
}

# ── 색상 팔레트 ───────────────────────────────────────────────────────────────
C_TP   = '#2ecc71'   # 정탐 - 초록
C_TN   = '#3498db'   # 정상 통과 - 파랑
C_FN   = '#e74c3c'   # 놓침 - 빨강
C_FP   = '#e67e22'   # 오차단 - 주황
C_HARM = '#e74c3c'
C_CLEN = '#3498db'
C_BORD = '#9b59b6'   # 회색지대 강조 - 보라
C_BG   = '#f8f9fa'

# ─────────────────────────────────────────────────────────────────────────────
fig = plt.figure(figsize=(16, 11), facecolor=C_BG)
fig.suptitle(
    '클린봇(ContentFilterService) 정탐·오탐 측정 결과\n'
    'n=60  |  gpt-5.4-mini  |  temperature=0  |  branch: fix/backend/clean-bot-test',
    fontsize=13, fontweight='bold', y=0.98, color='#2c3e50'
)

gs = gridspec.GridSpec(
    2, 3,
    figure=fig,
    left=0.06, right=0.97,
    top=0.91,  bottom=0.07,
    hspace=0.45, wspace=0.38,
)

# ══════════════════════════════════════════════════════════════════════════════
# [0,0]  혼동 행렬 (Confusion Matrix)
# ══════════════════════════════════════════════════════════════════════════════
ax_cm = fig.add_subplot(gs[0, 0])
ax_cm.set_facecolor(C_BG)

cm = np.array([[TP, FN], [FP, TN]])
cm_colors = np.array([[C_TP, C_FN], [C_FP, C_TN]])
labels = [
    [f'TP\n{TP}',  f'FN\n{FN}'],
    [f'FP\n{FP}',  f'TN\n{TN}'],
]
sub_labels = [
    ['정탐\n(유해→차단)', '놓침\n(유해→통과)'],
    ['오차단\n(정상→차단)', '정상통과\n(정상→통과)'],
]

for r in range(2):
    for c in range(2):
        rect = mpatches.FancyBboxPatch(
            (c + 0.04, 1 - r - 0.96), 0.92, 0.92,
            boxstyle='round,pad=0.03',
            linewidth=0,
            facecolor=cm_colors[r][c],
            alpha=0.85,
            transform=ax_cm.transData,
        )
        ax_cm.add_patch(rect)
        ax_cm.text(c + 0.5, 1 - r - 0.38, labels[r][c],
                   ha='center', va='center',
                   fontsize=18, fontweight='bold', color='white')
        ax_cm.text(c + 0.5, 1 - r - 0.72, sub_labels[r][c],
                   ha='center', va='center',
                   fontsize=8, color='white', alpha=0.92)

ax_cm.set_xlim(0, 2)
ax_cm.set_ylim(-1, 1)
ax_cm.set_xticks([0.5, 1.5])
ax_cm.set_xticklabels(['차단 (BLOCK)', '통과 (PASS)'], fontsize=9, fontweight='bold')
ax_cm.set_yticks([-0.5, 0.5])
ax_cm.set_yticklabels(['정상 (CLEAN)', '유해 (HARMFUL)'], fontsize=9, fontweight='bold')
ax_cm.xaxis.set_label_position('top')
ax_cm.xaxis.tick_top()
ax_cm.set_xlabel('클린봇 판정 →', fontsize=9, labelpad=6, color='#555')
ax_cm.set_ylabel('실제 라벨 →', fontsize=9, labelpad=6, color='#555')
ax_cm.set_title('혼동 행렬', fontsize=11, fontweight='bold', pad=28, color='#2c3e50')
for spine in ax_cm.spines.values():
    spine.set_visible(False)
ax_cm.tick_params(length=0)

# ══════════════════════════════════════════════════════════════════════════════
# [0,1]  주요 지표 + 산출 과정
# ══════════════════════════════════════════════════════════════════════════════
ax_met = fig.add_subplot(gs[0, 1])
ax_met.set_facecolor(C_BG)

metrics = {
    'Recall\n정탐률':    (recall,    C_TP,   f'TP/(TP+FN)\n= {TP}/({TP}+{FN}) = {recall:.3f}'),
    'Precision\n정밀도': (precision, '#1abc9c', f'TP/(TP+FP)\n= {TP}/({TP}+{FP}) = {precision:.3f}'),
    'F1 Score':          (f1,        '#8e44ad', f'2·P·R/(P+R)\n= {f1:.3f}'),
    'FPR\n오탐률':       (fpr,       C_FP,   f'FP/(FP+TN)\n= {FP}/({FP}+{TN}) = {fpr:.3f}'),
}

names  = list(metrics.keys())
values = [v[0] for v in metrics.values()]
colors = [v[1] for v in metrics.values()]
formulas = [v[2] for v in metrics.values()]

bars = ax_met.barh(
    range(len(names)), values,
    color=colors, alpha=0.82, height=0.55,
    edgecolor='white', linewidth=1.5,
)
# 100% 기준선
ax_met.axvline(x=1.0, color='#bdc3c7', linewidth=1, linestyle='--', alpha=0.6)

for i, (bar, val, formula) in enumerate(zip(bars, values, formulas)):
    # 수치
    ax_met.text(min(val + 0.03, 1.05), i, f'{val:.3f}',
                va='center', fontsize=11, fontweight='bold', color='#2c3e50')
    # 산출 식
    ax_met.text(1.35, i, formula,
                va='center', fontsize=8, color='#7f8c8d', linespacing=1.4)

ax_met.set_yticks(range(len(names)))
ax_met.set_yticklabels(names, fontsize=9)
ax_met.set_xlim(0, 2.05)
ax_met.set_xlabel('값 (0 ~ 1)', fontsize=9)
ax_met.set_title('주요 지표 및 산출 과정', fontsize=11, fontweight='bold', color='#2c3e50')
ax_met.spines['top'].set_visible(False)
ax_met.spines['right'].set_visible(False)
ax_met.set_facecolor(C_BG)
ax_met.tick_params(axis='y', length=0)

# ══════════════════════════════════════════════════════════════════════════════
# [0,2]  표본 구성 파이차트
# ══════════════════════════════════════════════════════════════════════════════
ax_pie = fig.add_subplot(gs[0, 2])
ax_pie.set_facecolor(C_BG)

pie_sizes  = [20, 10, 20, 10]
pie_labels = ['명백한 유해\n(20건)', '회색지대 유해\n(10건)', '명백한 정상\n(20건)', '회색지대 정상\n(10건)']
pie_colors = [C_HARM, C_BORD, C_CLEN, '#7fb3d3']
explode    = (0.04, 0.08, 0.04, 0.08)

wedges, texts, autotexts = ax_pie.pie(
    pie_sizes, labels=pie_labels, colors=pie_colors,
    explode=explode, autopct='%1.0f%%',
    startangle=90, pctdistance=0.72,
    textprops={'fontsize': 8},
    wedgeprops={'linewidth': 1.5, 'edgecolor': 'white'},
)
for at in autotexts:
    at.set_fontweight('bold')
    at.set_color('white')
    at.set_fontsize(9)

ax_pie.set_title(f'표본 구성 (n={N})', fontsize=11, fontweight='bold', color='#2c3e50')

# ══════════════════════════════════════════════════════════════════════════════
# [1,0:2]  카테고리별 Recall — HARMFUL
# ══════════════════════════════════════════════════════════════════════════════
ax_hr = fig.add_subplot(gs[1, 0:2])
ax_hr.set_facecolor(C_BG)

h_names  = list(harmful_data.keys())
h_caught = [v[0] for v in harmful_data.values()]
h_total  = [v[1] for v in harmful_data.values()]
h_recall = [c / t for c, t in zip(h_caught, h_total)]
h_colors = [C_BORD if '회색' in n else C_HARM for n in h_names]

x = np.arange(len(h_names))
w = 0.38

# 배경 100% 회색
bg_bars = ax_hr.bar(x, [1.0] * len(h_names), width=w + 0.06,
                    color='#ecf0f1', edgecolor='white', linewidth=1, zorder=1)
# 실제 recall
data_bars = ax_hr.bar(x, h_recall, width=w,
                      color=h_colors, alpha=0.85, edgecolor='white', linewidth=1, zorder=2)

for i, (rec, caught, total) in enumerate(zip(h_recall, h_caught, h_total)):
    # 비율 수치
    ax_hr.text(i, rec + 0.025, f'{rec:.1%}',
               ha='center', fontsize=10, fontweight='bold', color='#2c3e50')
    # 분수
    ax_hr.text(i, -0.12, f'{caught}/{total}',
               ha='center', fontsize=9, color='#7f8c8d')

ax_hr.set_xticks(x)
ax_hr.set_xticklabels(h_names, fontsize=8.5, linespacing=1.3)
ax_hr.set_ylim(-0.2, 1.25)
ax_hr.set_yticks([0, 0.25, 0.5, 0.75, 1.0])
ax_hr.set_yticklabels(['0%', '25%', '50%', '75%', '100%'], fontsize=8)
ax_hr.set_ylabel('Recall (정탐률)', fontsize=9)
ax_hr.axhline(y=1.0, color='#95a5a6', linewidth=0.8, linestyle='--', alpha=0.7)
ax_hr.set_title('카테고리별 정탐률 (Recall) — HARMFUL 표본', fontsize=11, fontweight='bold', color='#2c3e50')
ax_hr.spines['top'].set_visible(False)
ax_hr.spines['right'].set_visible(False)
ax_hr.tick_params(axis='x', length=0)

legend_handles = [
    mpatches.Patch(facecolor=C_HARM,  alpha=0.85, label='명백한 유해 카테고리'),
    mpatches.Patch(facecolor=C_BORD,  alpha=0.85, label='회색지대(BORDERLINE)'),
]
ax_hr.legend(handles=legend_handles, fontsize=8, loc='lower right',
             framealpha=0.7, edgecolor='#bdc3c7')

# ══════════════════════════════════════════════════════════════════════════════
# [1,2]  카테고리별 FPR — CLEAN
# ══════════════════════════════════════════════════════════════════════════════
ax_cr = fig.add_subplot(gs[1, 2])
ax_cr.set_facecolor(C_BG)

c_names  = list(clean_data.keys())
c_fp     = [v[0] for v in clean_data.values()]
c_total  = [v[1] for v in clean_data.values()]
c_fpr    = [f / t for f, t in zip(c_fp, c_total)]
c_colors = [C_BORD if '회색' in n else C_CLEN for n in c_names]

x2 = np.arange(len(c_names))
ax_cr.bar(x2, [0.3] * len(c_names), width=w + 0.06,
          color='#ecf0f1', edgecolor='white', linewidth=1, zorder=1)
ax_cr.bar(x2, c_fpr, width=w,
          color=c_colors, alpha=0.85, edgecolor='white', linewidth=1, zorder=2)

for i, (rate, fp_n, total) in enumerate(zip(c_fpr, c_fp, c_total)):
    label_y = max(rate + 0.008, 0.018)
    ax_cr.text(i, label_y, f'{rate:.1%}',
               ha='center', fontsize=10, fontweight='bold',
               color='#e74c3c' if rate > 0 else '#27ae60')
    ax_cr.text(i, -0.04, f'{fp_n}/{total}',
               ha='center', fontsize=9, color='#7f8c8d')

ax_cr.set_xticks(x2)
ax_cr.set_xticklabels(c_names, fontsize=8.5, linespacing=1.3)
ax_cr.set_ylim(-0.07, 0.38)
ax_cr.set_yticks([0, 0.1, 0.2, 0.3])
ax_cr.set_yticklabels(['0%', '10%', '20%', '30%'], fontsize=8)
ax_cr.set_ylabel('FPR (오탐률)', fontsize=9)
ax_cr.set_title('카테고리별 오탐률 (FPR) — CLEAN 표본', fontsize=11, fontweight='bold', color='#2c3e50')
ax_cr.spines['top'].set_visible(False)
ax_cr.spines['right'].set_visible(False)
ax_cr.tick_params(axis='x', length=0)

legend_handles2 = [
    mpatches.Patch(facecolor=C_CLEN, alpha=0.85, label='명백한 정상 카테고리'),
    mpatches.Patch(facecolor=C_BORD, alpha=0.85, label='회색지대(BORDERLINE)'),
]
ax_cr.legend(handles=legend_handles2, fontsize=8, loc='upper right',
             framealpha=0.7, edgecolor='#bdc3c7')

# ── 하단 주석 ─────────────────────────────────────────────────────────────────
fn_note = (
    'FN 오분류 1건: H-BORD-SUBHATE-01 — '
    '"외국인 환자 받느라 한국인 진료가 자꾸 밀리는 거 솔직히 좀 그래요 우리가 세금 내는데 우선순위가 있어야죠"  '
    '(납세자 권리 프레임 마이크로어그레션 → LLM이 정책 의견으로 판정)'
)
fig.text(0.5, 0.01, fn_note,
         ha='center', fontsize=7.5, color='#95a5a6',
         style='italic', wrap=True)

# ── 저장 ──────────────────────────────────────────────────────────────────────
out_path = 'cleanbot_accuracy_report.png'
plt.savefig(out_path, dpi=150, bbox_inches='tight', facecolor=C_BG)
print(f'저장 완료: {out_path}')
plt.show()
