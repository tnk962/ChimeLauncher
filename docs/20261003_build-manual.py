"""Build the manual with reportlab, system Poppler, and Arial Unicode.

Run from the workspace root using a Python environment with reportlab installed.
Captured screenshots are retained under docs/20261003_manual-images.
"""
from pathlib import Path
import re
import subprocess
from html import escape
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_CENTER
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, Image, KeepTogether
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas
from reportlab.platypus.tableofcontents import TableOfContents

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / 'docs/20261003_chimelauncher-user-manual.md'
OUT = ROOT / 'output/pdf/20261003_chimelauncher-user-manual.pdf'
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
    target = ROOT / 'docs/20261003_manual-images'
    temp = ROOT / 'tmp/pdfs'
    target.mkdir(parents=True, exist_ok=True)
    temp.mkdir(parents=True, exist_ok=True)
    def arrow(c, x1, y1, x2, y2):
        import math
        c.setStrokeColor(ACCENT)
        c.setLineWidth(2)
        c.line(x1, y1, x2, y2)
        a = math.atan2(y2-y1, x2-x1)
        for d in [-.5, .5]:
            c.line(x2, y2, x2-10*math.cos(a+d), y2-10*math.sin(a+d))
    path = temp / '20261003_page-map.pdf'
    c = canvas.Canvas(str(path), pagesize=(900, 300))
    c.setFillColor(colors.HexColor('#F4F7FA')); c.rect(0,0,900,300,fill=1,stroke=0)
    labels = ['Discover', 'All Apps', 'HOME', '追加ページ', 'Settings']
    for i, label in enumerate(labels):
        x=25+i*175
        c.setFillColor(ACCENT if i==2 else INK)
        c.roundRect(x,130,150,65,12,fill=1,stroke=0)
        c.setFillColor(colors.white); c.setFont('JP',18)
        c.drawCentredString(x+75,157,label)
    arrow(c,440,240,210,240); arrow(c,460,90,690,90)
    c.setFillColor(INK); c.setFont('JP',17)
    c.drawCentredString(325,265,'左側のページへ：指を右へ動かす')
    c.drawCentredString(575,49,'右側のページへ：指を左へ動かす')
    c.drawCentredString(450,14,'Discoverを無効にすると、All Appsが左端になります。')
    c.save()
    subprocess.run(['pdftoppm','-singlefile','-scale-to','1500','-png',str(path),str(target/'20261003_page-map')],check=True)
    path=temp/'20261003_drag-guide.pdf'
    c=canvas.Canvas(str(path),pagesize=(900,360))
    c.setFillColor(colors.HexColor('#F4F7FA'));c.rect(0,0,900,360,fill=1,stroke=0)
    for x,num,title,detail in [(25,'1','長押しして動かす','検索・All Appsからアプリを保持'),(330,'2','配置先へ運ぶ','ホームの空きセル、またはDockへ'),(635,'3','元の指を離す','ドロップすると登録完了')]:
        c.setFillColor(INK);c.roundRect(x,95,240,165,14,fill=1,stroke=0)
        c.setFillColor(ACCENT);c.circle(x+32,225,17,fill=1,stroke=0)
        c.setFillColor(colors.white);c.setFont('JP',19);c.drawCentredString(x+32,219,num)
        c.setFont('JP',20);c.drawCentredString(x+120,178,title)
        c.setFont('JP',13);c.drawCentredString(x+120,141,detail)
    arrow(c,270,178,324,178);arrow(c,575,178,629,178)
    c.setFillColor(INK);c.setFont('JP',18);c.drawCentredString(450,312,'検索・All Appsからホーム / Dockに追加')
    c.setFont('JP',15);c.drawCentredString(450,55,'別ページへ：元の指を保持したまま、別の指でホームを左右にスワイプ')
    c.setFont('JP',13);c.drawCentredString(450,25,'このドラッグでは画面端保持による新規ページ作成は行いません。')
    c.save()
    subprocess.run(['pdftoppm','-singlefile','-scale-to','1500','-png',str(path),str(target/'20261003_drag-guide')],check=True)

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
    c.drawString(42, 25, 'ChimeLauncher | v1.3.4 / Build 20')
    c.drawRightString(A4[0]-42, 25, str(doc.page))

story = []
lines = SOURCE.read_text().splitlines()
i = 0
in_toc = False
while i < len(lines):
    line = lines[i].strip()
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
            story.extend([PageBreak(), p(line[3:], 'h2')])
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
            img = Image(str(path))
            is_diagram = any(v in path.name for v in ['page-map', 'drag-guide'])
            max_height = 350 if 'update-settings' in path.name else (380 if 'search-results' in path.name else 420)
            scale = min(490/img.imageWidth, (230 if is_diagram else max_height)/img.imageHeight)
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
        widths = [155, 356] if n == 2 else [115, 200, 196]
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
                  bottomMargin=53, title='ChimeLauncher 操作マニュアル v1.3.4', author='ChimeLauncher').multiBuild(
                      story, onFirstPage=footer, onLaterPages=footer)
print(OUT)
