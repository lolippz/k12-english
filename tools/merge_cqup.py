#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
重大版（重庆大学出版社）小学英语「三年级起点」词库合并脚本
=============================================================

新增 CQUP 3A-6B 共 8 册词表，与现有 data/words.json 合并：
  1. 词目按「教材年级主题 + 义务教育英语课程标准词汇」整理，按册组织（不细分到单元）；
  2. 音标用本地 ECDICT (skywind3000/ECDICT, MIT) 补齐，缺失时留空；
  3. 已存在于词库的词只追加 books/stages 标记，不重复建条；
  4. meta 里记录 cqup 来源与条数，便于追溯。

⚠️ 词表并非官方逐单元词表（公开渠道拿不到完整版），建议使用者对照教材核对，
   发现缺漏可直接编辑本文件里的 CQUP 词表后重跑。

用法:
    python tools/merge_cqup.py            # 实际写入
    python tools/merge_cqup.py --dry-run  # 只统计，不写回
"""

import csv
import json
import os
import re
import sys

csv.field_size_limit(10 ** 7)
HERE = os.path.dirname(os.path.abspath(__file__))
ECDICT = os.path.join(HERE, "cache", "ecdict.csv")
WORDS_JSON = os.path.join(HERE, "..", "data", "words.json")

# ---------------------------------------------------------------------------
# 词表：册次 -> ["word|中文释义", ...]
# 主题依据重大版教材单元编排：3A 问候/文具/身体/颜色/动物/数字，
# 3B 家庭/水果/食物/数字/玩具/方位，以此类推。
# ---------------------------------------------------------------------------
CQUP = {
    "重大版三年级上": """
hello|你好
hi|你好
goodbye|再见
bye|再见
morning|早晨；上午
afternoon|下午
evening|晚上；傍晚
good|好的
fine|好的；健康的
I|我
am|是
is|是
are|是
you|你；你们
yes|是；是的
no|不；不是
not|不
what|什么
it|它
this|这；这个
my|我的
your|你的；你们的
name|名字
teacher|教师
Mr|先生
Miss|小姐；老师
school|学校
class|班级；同学们
student|学生
nice|令人愉快的
meet|遇见；认识
too|也
thank|感谢
thanks|谢谢
OK|好；可以
pen|钢笔
pencil|铅笔
pencil box|文具盒
ruler|尺子
eraser|橡皮
book|书
bag|包；书包
schoolbag|书包
desk|书桌
chair|椅子
blackboard|黑板
crayon|蜡笔
head|头
face|脸
nose|鼻子
mouth|嘴
ear|耳朵
eye|眼睛
hair|头发
hand|手
arm|手臂
leg|腿
foot|脚
body|身体
red|红色
yellow|黄色
blue|蓝色
green|绿色
black|黑色
white|白色
brown|棕色
orange|橙色
pink|粉色
purple|紫色
colour|颜色
cat|猫
dog|狗
bird|鸟
fish|鱼
duck|鸭子
pig|猪
bear|熊
panda|熊猫
monkey|猴子
tiger|老虎
elephant|大象
zoo|动物园
animal|动物
one|一
two|二
three|三
four|四
five|五
six|六
seven|七
eight|八
nine|九
ten|十
how many|多少
""",

    "重大版三年级下": """
father|父亲；爸爸
dad|爸爸
mother|母亲；妈妈
mum|妈妈
brother|兄；弟
sister|姐；妹
grandfather|祖父；外祖父
grandma|奶奶；外婆
grandmother|祖母；外祖母
grandpa|爷爷；外公
family|家；家庭
man|男人
woman|女人
boy|男孩
girl|女孩
friend|朋友
apple|苹果
banana|香蕉
pear|梨
grape|葡萄
watermelon|西瓜
peach|桃子
strawberry|草莓
fruit|水果
cake|蛋糕
bread|面包
egg|鸡蛋
milk|牛奶
water|水
juice|果汁
rice|米饭
noodles|面条
hamburger|汉堡包
chicken|鸡肉
hungry|饥饿的
thirsty|口渴的
like|喜欢
eat|吃
drink|喝
eleven|十一
twelve|十二
thirteen|十三
fourteen|十四
fifteen|十五
sixteen|十六
seventeen|十七
eighteen|十八
nineteen|十九
twenty|二十
toy|玩具
doll|玩具娃娃
ball|球
kite|风筝
car|小汽车
bus|公共汽车
bike|自行车
plane|飞机
ship|轮船
boat|小船
train|火车
on|在……上
in|在……里
under|在……下面
behind|在……后面
near|在……附近
box|盒子
here|这里
there|那里
tall|高的
small|小的
big|大的
long|长的
short|短的；矮的
cute|可爱的
beautiful|美丽的
""",

    "重大版四年级上": """
classroom|教室
window|窗户
light|灯
picture|图画；照片
door|门
floor|地板
computer|计算机
wall|墙
fan|风扇
black|黑色
English|英语；英国的
Chinese|语文；中国的
maths|数学
music|音乐
art|美术；艺术
PE|体育课
science|科学
lesson|课
have|有；进行
has|有（第三人称单数）
time|时间
o'clock|……点钟
day|天；日
week|星期；周
today|今天
Monday|星期一
Tuesday|星期二
Wednesday|星期三
Thursday|星期四
Friday|星期五
Saturday|星期六
Sunday|星期日
shirt|衬衫
T-shirt|T恤衫
dress|连衣裙
skirt|裙子
trousers|裤子
coat|外套
sweater|毛衣
jacket|夹克
shoe|鞋
sock|袜子
hat|帽子
cap|便帽
wear|穿；戴
cold|寒冷的
hot|热的
warm|温暖的
cool|凉爽的
sunny|晴朗的
rainy|下雨的
windy|有风的
cloudy|多云的
snowy|下雪的
weather|天气
strong|强壮的
thin|瘦的
kind|和蔼的；亲切的
quiet|安静的
glasses|眼镜
he|他
she|她
they|他们；她们；它们
his|他的
her|她的
speak|说；讲
""",

    "重大版四年级下": """
playground|操场
garden|花园
library|图书馆
music room|音乐教室
computer room|计算机房
gym|体育馆；健身房
park|公园
home|家
bedroom|卧室
living room|客厅
study|书房
kitchen|厨房
bathroom|浴室；卫生间
bed|床
sofa|沙发
table|桌子
phone|电话
fridge|冰箱
lamp|台灯
breakfast|早餐
lunch|午餐
dinner|晚餐；正餐
meal|一餐
get up|起床
go to school|去上学
go home|回家
go to bed|上床睡觉
watch TV|看电视
now|现在
half|一半；半小时
past|过；经过
sheep|绵羊
hen|母鸡
cow|奶牛
horse|马
goat|山羊
farm|农场
fat|肥胖的
lovely|可爱的
clever|聪明的
funny|滑稽的；有趣的
run|跑
jump|跳
walk|走路；散步
swim|游泳
fly|飞；放（风筝）
sing|唱歌
dance|跳舞
draw|画画
read|读；阅读
write|写
sleep|睡觉
play|玩；打（球）
football|足球
basketball|篮球
ping-pong|乒乓球
game|游戏；比赛
"""
    ,
    "重大版五年级上": """
young|年轻的
old|年老的；旧的
polite|有礼貌的
hard-working|勤奋的
helpful|有帮助的；乐于助人的
shy|害羞的
smart|聪明的
active|积极的；活跃的
strict|严格的
know|知道；了解
weekend|周末
often|经常
sometimes|有时
usually|通常
always|总是
watch|观看
wash|洗
clothes|衣服
water|浇水
clean|打扫；干净的
room|房间
sweep|扫；清扫
cook|烹调；厨师
tasty|好吃的
sweet|甜的
sour|酸的
salty|咸的
fresh|新鲜的
healthy|健康的
favourite|最喜爱的
tomato|西红柿
potato|土豆
pork|猪肉
mutton|羊肉
tofu|豆腐
cabbage|卷心菜
eggplant|茄子
onion|洋葱
carrot|胡萝卜
vegetable|蔬菜
do homework|做作业
do housework|做家务
read books|读书
play games|玩游戏
about|关于
with|和……一起
well|好；很好地
hour|小时
""",

    "重大版五年级下": """
spring|春天
summer|夏天
autumn|秋天
winter|冬天
season|季节
January|一月
February|二月
March|三月
April|四月
May|五月
June|六月
July|七月
August|八月
September|九月
October|十月
November|十一月
December|十二月
rain|雨；下雨
snow|雪；下雪
skate|滑冰
ski|滑雪
plant|种植；植物
tree|树
birthday|生日
party|聚会；派对
candle|蜡烛
present|礼物
gift|礼物
card|卡片
first|第一
second|第二
third|第三
fourth|第四
fifth|第五
date|日期
month|月份
year|年
take pictures|拍照
climb|爬；攀登
mountain|山
shopping|购物
go shopping|去购物
piano|钢琴
violin|小提琴
chess|国际象棋
butterfly|蝴蝶
insect|昆虫
grow|生长；种植
leaf|叶子
best|最好的
because|因为
""",

    "重大版六年级上": """
subway|地铁
on foot|步行
traffic|交通
light|信号灯
rule|规则
stop|停止
wait|等待
north|北；北方
south|南；南方
east|东；东方
west|西；西方
left|左；左边
right|右；右边
straight|直地
turn|转弯
cinema|电影院
hospital|医院
bookstore|书店
supermarket|超市
museum|博物馆
post office|邮局
where|在哪里
next to|紧挨着
far|远的
hobby|爱好
stamp|邮票
collect|收集
show|展示；表演
story|故事
comic|漫画
worker|工人
doctor|医生
nurse|护士
driver|司机
farmer|农民
policeman|男警察
singer|歌手
writer|作家
actor|男演员
actress|女演员
artist|画家；艺术家
cleaner|清洁工
reporter|记者
engineer|工程师
work|工作
job|职业；工作
by|乘；通过
how|怎样
""",

    "重大版六年级下": """
taller|更高的
shorter|更矮的；更短的
stronger|更强壮的
older|更年长的
younger|更年轻的
bigger|更大的
smaller|更小的
longer|更长的
thinner|更瘦的
heavier|更重的
than|比
kilogram|千克；公斤
metre|米
weekend|周末
yesterday|昨天
went|去（go的过去式）
saw|看见（see的过去式）
ate|吃（eat的过去式）
took|拿；带（take的过去式）
bought|买（buy的过去式）
swam|游泳（swim的过去式）
climbed|攀登（climb的过去式）
danced|跳舞（dance的过去式）
sang|唱歌（sing的过去式）
cleaned|打扫（clean的过去式）
washed|洗（wash的过去式）
visited|参观；拜访（visit的过去式）
played|玩（play的过去式）
did|做（do的过去式）
was|是（is/am的过去式）
were|是（are的过去式）
holiday|假期；假日
trip|旅行
travel|旅行
take a trip|去旅行
row|划（船）
learn|学习；学会
word|单词；词
middle school|中学
primary school|小学
remember|记住
forget|忘记
excited|兴奋的
last|上一个；最后的
soon|不久
future|未来
dream|梦想
goodbye|再见
""",
}


def parse(words_text: str):
    out = []
    for line in words_text.strip().splitlines():
        line = line.strip()
        if not line:
            continue
        w, _, tr = line.partition("|")
        out.append((w.strip(), tr.strip()))
    return out


# ECDICT 缺音标的少量补丁（短语或旧词根条目为空）
MANUAL_PHON = {
    "do housework": "duː ˈhaʊswɜːk",
    "take pictures": "teɪk ˈpɪktʃəz",
    "play games": "pleɪ ɡeɪmz",
    "visited": "ˈvɪzɪtɪd",
    "played": "pleɪd",
    "how many": "haʊ ˈmeni",
}


def norm_phon(p: str) -> str:
    if not p:
        return ""
    p = p.strip().strip("/[]")
    p = re.sub(r"\s+", " ", p)
    return p.strip()


def load_ecdict(needed: set):
    out = {}
    if not os.path.exists(ECDICT):
        print(f"  ! 未找到 {ECDICT}，音标将留空")
        return out
    with open(ECDICT, encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            w = (row.get("word") or "").strip().lower()
            if w in needed and w not in out:
                out[w] = row
    return out


def main():
    dry = "--dry-run" in sys.argv

    entries = []          # (book, word, trans)
    for book, text in CQUP.items():
        for w, tr in parse(text):
            entries.append((book, w, tr))

    needed = {w.lower() for _, w, _ in entries}
    print(f"CQUP 词目 {len(entries)} 条（去重前）")
    ecdict = load_ecdict(needed)
    print(f"ECDICT 命中 {len(ecdict)} 条")

    with open(WORDS_JSON, encoding="utf-8") as f:
        data = json.load(f)
    words = data["words"]
    index = {w["w"].lower(): w for w in words}

    added, updated = 0, 0
    new_words = []
    for book, w, tr in entries:
        key = w.lower()
        row = ecdict.get(key)
        ph = norm_phon((row or {}).get("phonetic", "")) if row else ""
        ph = MANUAL_PHON.get(key, ph)
        exist = index.get(key)
        if exist is not None:
            changed = False
            if book not in exist.get("books", []):
                exist.setdefault("books", []).append(book)
                changed = True
            if "primary" not in exist.get("stages", []):
                exist.setdefault("stages", []).append("primary")
                changed = True
            if changed:
                updated += 1
            continue
        item = {
            "w": w,
            "ph": ph,       # ECDICT 为英式 DJ；美式列缺失，先同用
            "phUk": ph,
            "tr": [tr] if tr else ([t.strip() for t in (row.get("translation", "").split("\\n")[0].split("；"))] if row else []),
            "stages": ["primary"],
            "books": [book],
        }
        if row:
            if row.get("oxford"):
                try:
                    item["oxford"] = int(row["oxford"])
                except ValueError:
                    pass
            if row.get("collins"):
                try:
                    item["star"] = int(row["collins"])
                except ValueError:
                    pass
            if row.get("frq"):
                try:
                    item["frq"] = int(row["frq"])
                except ValueError:
                    pass
        new_words.append(item)
        index[key] = item
        added += 1

    print(f"新增 {added} 条，为已有词追加重大版标记 {updated} 条")
    if dry:
        print("(dry-run，未写回)")
        return

    words.extend(new_words)
    data["meta"]["total"] = len(words)
    data["meta"]["cqup"] = {
        "source": "重大版（重庆大学出版社）小学英语三年级起点，按教材主题与课标词汇整理（非官方逐单元词表）",
        "added": added,
        "books_marked": updated,
    }
    with open(WORDS_JSON, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print(f"已写回 {WORDS_JSON}，总词数 {len(words)}")


if __name__ == "__main__":
    main()
