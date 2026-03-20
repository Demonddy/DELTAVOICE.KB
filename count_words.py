import re

with open(r'c:\Users\rrr\Desktop\keyboard\app\src\main\java\com\deltavoice\PredictiveWordList.kt', 'r', encoding='utf-8') as f:
    content = f.read()

pattern = r'private val (\w+Words).*?wordsFromString\(\s*([\s\S]*?)\s*\)\}'
matches = re.findall(pattern, content)

for name, block in matches:
    text = block.replace('"', '').replace('+', ' ').replace('\n', ' ').replace('\r', ' ')
    words = [w.strip() for w in text.split() if w.strip()]
    if words and words[0].startswith('PLACEHOLDER'):
        print(f'{name}: PLACEHOLDER')
    else:
        print(f'{name}: {len(words)} words')
