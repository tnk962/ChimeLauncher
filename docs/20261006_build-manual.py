"""Build the manual with reportlab, system Poppler, and Arial Unicode.

Run from the workspace root using a Python environment with reportlab installed.
Captured screenshots are retained under docs/20261006_manual-images.
"""
from pathlib import Path
import re
import subprocess
from html import escape
from PIL import Image as RasterImage
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_CENTER
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, Image, KeepTogether, CondPageBreak
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas
from reportlab.platypus.tableofcontents import TableOfContents

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'docs/20261006_chimelauncher-user-manual.md'
OUT = ROOT / 'output/pdf/20261006_chimelauncher-user-manual.pdf'
pdfmetrics.registerFont(TTFont('JP', '/Library/Fonts/Arial Unicode.ttf'))
pdfmetrics.registerFontFamily('JP', normal='JP', bold='JP', italic='JP', boldItalic='JP')
INK = colors.HexColor('#203347')
ACCENT = colors.HexColor('#24778A')
styles = {
    'body': ParagraphStyle('body', fontName='JP', fontSize=10, leading=16, wordWrap='CJK', textColor=INK, spaceAfter=7),
    'h1': ParagraphStyle('h1', fontName='JP', fontSize=27, leading=38, textColor=INK, spaceAfter=18),
    'h2': ParagraphStyle('h2', fontName='JP', fontSize=18, leading=27, textColor=ACCENT, spaceAfter=14, keepWithNext=True),
    'h3': ParagraphStyle('h3', fontName='JP', fontSize=12, leading=19, textColor=ACCENT, spaceBefore=11, spaceAfter=6, keepWithNext=True),
    'caption': ParagraphStyle('caption', fontName='JP', fontSize=8, leading=12, wordWrap='CJK', textColor=INK, alignment=TA_CENTER, spaceAfter=10),
    'cell': ParagraphStyle('cell', fontName='JP', fontSize=8.5, leading=13, wordWrap='CJK', textColor=INK),
}

def make_diagrams():
    target = ROOT / 'docs/20261006_manual-images'
    temp = ROOT / 'tmp/pdfs'
    target.mkdir(parents=True, exist_ok=True)
    temp.mkdir(parents=True, exist_ok=True)
    path = temp / '20261006_page-map.pdf'
    c = canvas.Canvas(str(path), pagesize=(1200, 680))
    c.setFillColor(colors.HexColor('#F4F7FA')); c.rect(0,0,1200,680,fill=1,stroke=0)
    c.setFont('JP', 24); c.setFillColor(INK)
    c.drawString(25,630,'ページ・フィード表示：HOMEから左側へ進む順序')
    combos = [('Google ON / 独自 ON', ['HOME','独自フィード','Google Discover']),
              ('Google ON / 独自 OFF', ['HOME','Google Discover']),
              ('Google OFF / 独自 ON', ['HOME','独自フィード']),
              ('Google OFF / 独自 OFF', ['HOME'])]
    for block, apps_on in enumerate([True, False]):
        y0 = 565-block*270
        c.setFillColor(ACCENT); c.setFont('JP',20); c.drawString(25,y0,'All Apps '+('ON' if apps_on else 'OFF'))
        for row, (label, pages) in enumerate(combos):
            y = y0-52-row*51
            pages = [pages[0]] + (['All Apps'] if apps_on else []) + pages[1:]
            c.setFillColor(INK); c.setFont('JP',15); c.drawString(25,y+13,label)
            for j, page in enumerate(pages):
                x=290+j*211
                c.setFillColor(ACCENT if j==0 else INK); c.roundRect(x,y,185,39,6,fill=1,stroke=0)
                c.setFillColor(colors.white); c.setFont('JP',16); c.drawCentredString(x+92,y+12,page)
                if j:
                    c.setFillColor(ACCENT); c.setFont('JP',18); c.drawCentredString(x-14,y+11,'→')
    c.setFillColor(INK);c.setFont('JP',16);c.drawString(25,24,'左側へ進むときは、指を右へ動かします。上スワイプ検索はAll Apps OFFでも使えます。')
    c.save()
    subprocess.run(['pdftoppm','-singlefile','-scale-to','1600','-png',str(path),str(target/'20261006_page-map')],check=True)

make_diagrams()

def rich(s):
    s = escape(s)
    s = re.sub(r'\[([^\]]+)\]\((https?://[^)]+)\)', r'<link href="\2" color="#24778A">\1</link>', s)
    s = re.sub(r'`([^`]+)`', r'\1', s)
    return re.sub(r'\*\*([^*]+)\*\*', r'<b>\1</b>', s)

def p(s, style='body'):
    return Paragraph(rich(s), styles[style])

def footer(c, doc):
    c.setStrokeColor(colors.HexColor('#DCE5EC'))
    c.line(42, 39, A4[0]-42, 39)
    c.setFont('JP', 8)
    c.setFillColor(INK)
    c.drawString(42, 25, 'ChimeLauncher | v1.3.8 / Build 42')
    c.drawRightString(A4[0]-42, 25, str(doc.page))

story = []
lines = SOURCE.read_text().splitlines()
i = 0
in_toc = False
while i < len(lines):
    line = lines[i].strip()
    if line.startswith('*図') and line.endswith('*'):
        i += 1
        continue
    if line.startswith('[PDF版をダウンロード]'):
        i += 1
        continue
    if not line:
        i += 1
        continue
    if line.startswith('# '):
        icon = Image(str(ROOT / 'app/src/main/ic_launcher-playstore.png'), width=85, height=85)
        icon.hAlign = 'LEFT'
        story.extend([Spacer(1, 45), icon, Spacer(1, 22), p('CHIME LAUNCHER', 'h3'), p(line[2:], 'h1')])
    elif line.startswith('## '):
        if line == '## このマニュアルの確認範囲':
            story.extend([Spacer(1, 15), p(line[3:], 'h3')])
        else:
            story.extend([PageBreak() if line in ('## 目次', '## 1. インストールと初期設定') else CondPageBreak(400), Spacer(1, 14), p(line[3:], 'h2')])
        in_toc = line == '## 目次'
        if in_toc:
            toc = TableOfContents()
            toc.levelStyles = [ParagraphStyle('toc', fontName='JP', fontSize=11,
                                leading=24, textColor=INK, wordWrap='CJK')]
            story.append(toc)
    elif line.startswith('### '):
        story.append(p(line[4:], 'h3'))
    elif line.startswith('!['):
        m = re.match(r'!\[([^]]*)\]\(([^)]+)\)', line)
        if m:
            path = SOURCE.parent / m[2]
            is_diagram = any(v in path.name for v in ['page-map', 'drag-guide'])
            embed_path = path
            if not is_diagram:
                # Optimize PDF delivery only; retain original lossless screenshots for GitHub.
                cache = ROOT / 'tmp/pdfs/20261006_pdf-images'
                cache.mkdir(parents=True, exist_ok=True)
                embed_path = cache / (path.stem + '.jpg')
                with RasterImage.open(path) as screenshot:
                    screenshot.convert('RGB').save(embed_path, 'JPEG', quality=92, subsampling=0, optimize=True)
            img = Image(str(embed_path))
            max_height = 500
            scale = min(490/img.imageWidth, (310 if is_diagram else max_height)/img.imageHeight)
            img.drawWidth, img.drawHeight = img.imageWidth*scale, img.imageHeight*scale
            img.keepWithNext = True
            gap = Spacer(1, 5)
            gap.keepWithNext = True
            story.extend([img, gap, p(m[1], 'caption')])
    elif line.startswith('|'):
        rows = []
        while i < len(lines) and lines[i].strip().startswith('|'):
            row = lines[i].strip().strip('|').split('|')
            if not all(re.match(r'^:?-+:?$', v.strip()) for v in row):
                rows.append([p(v.strip(), 'cell') for v in row])
            i += 1
        n = len(rows[0])
        widths = {2: [155, 356], 3: [115, 200, 196], 4: [60, 85, 85, 281]}[n]
        t = Table(rows, colWidths=widths, repeatRows=1, hAlign='LEFT')
        t.setStyle(TableStyle([
            ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#DCEEF1')),
            ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, colors.HexColor('#F4F7FA')]),
            ('VALIGN', (0,0), (-1,-1), 'TOP'),
            ('LEFTPADDING', (0,0), (-1,-1), 9), ('RIGHTPADDING', (0,0), (-1,-1), 9),
            ('TOPPADDING', (0,0), (-1,-1), 7), ('BOTTOMPADDING', (0,0), (-1,-1), 7),
            ('LINEBELOW', (0,0), (-1,0), .6, ACCENT),
        ]))
        story.extend([t, Spacer(1, 10)])
        continue
    else:
        if not in_toc:
            story.append(p(line))
    i += 1

OUT.parent.mkdir(parents=True, exist_ok=True)
class ManualDoc(SimpleDocTemplate):
    def afterFlowable(self, flowable):
        if isinstance(flowable, Paragraph) and flowable.style.name == 'h2':
            title = flowable.getPlainText()
            if re.match(r'^\d+\.', title):
                key = 'chapter-' + title.split('.')[0]
                self.canv.bookmarkPage(key)
                self.canv.addOutlineEntry(title, key, level=0)
                self.notify('TOCEntry', (0, title, self.page, key))

ManualDoc(str(OUT), pagesize=A4, rightMargin=42, leftMargin=42, topMargin=45,
                  bottomMargin=53, title='ChimeLauncher 操作マニュアル v1.3.8', author='ChimeLauncher').multiBuild(
                      story, onFirstPage=footer, onLaterPages=footer)
print(OUT)
