const fs = require('fs');
const filePath = 'app/src/main/java/com/deltavoice/PredictiveWordList.kt';
let content = fs.readFileSync(filePath, 'utf8');

const additions = {

arabicWords: `شجرة ورقة زهرة ثمرة بذرة جذر ساق غصن لحاء عشب حشيش قمح شعير ذرة أرز قطن كتان نخلة زيتون تفاحة مشمش خوخ تين رمان توت كرز بلح جوز لوز فستق كاجو بندق سمسم كمون كركم زعفران قرفة زنجبيل نعناع بقدونس كزبرة حبق ريحان زعتر شبت طحين سميد نشاء خميرة مربى عسل زبادي لبن قشطة كريمة صلصة خردل فلفل شطة بهار توابل مخلل سلطة حساء مرق شوربة فول حمص عدس فاصوليا بازلاء ذرة لوبيا سبانخ ملوخية بامية كوسة قرع فجل شمندر ملفوف خس جرجير فجل كرفس خرشوف باذنجان شريحة قطعة كمية وزن حجم طول عرض ارتفاع مساحة سمك لتر كيلو غرام متر كيلومتر سنتيمتر ملليمتر ميل قدم بوصة درجة زاوية مستطيل مثلث دائرة مربع نجمة شكل رسم خط منحنى نقطة مركز نصف ربع ثلث خمس عشر مضاعف نصف ضعف مقياس نسبة معدل متوسط مجموع حاصل فرق ناتج عامل معامل قسمة ضرب جمع طرح أس جذر تربيعي معادلة صيغة رمز حرف رقم عدد ترتيب تسلسل سلسلة قائمة جدول مخطط رسم بياني إحصاء احتمال نظرية برهان دليل حقيقة خطأ صواب تقريب تعريف مصطلح مفهوم تصنيف مقارنة تحليل تركيب استنتاج استقراء فرضية تجريبي نظري عملي تطبيقي أكاديمي مهني حرفي تجاري صناعي زراعي`,

hindiWords: `पेड़ पत्ता फूल फल बीज जड़ तना शाखा छाल घास गेहूँ जौ मक्का धान कपास सन ताड़ जैतून सेब खुबानी आड़ू अंजीर अनार शहतूत चेरी खजूर अखरोट बादाम पिस्ता काजू मूँगफली तिल जीरा हल्दी केसर दालचीनी अदरक पुदीना अजमोद धनिया तुलसी अजवाइन सौंफ आटा सूजी स्टार्च खमीर मुरब्बा शहद दही छाछ मलाई क्रीम चटनी सरसों काली मसाला अचार सलाद सूप शोरबा राजमा चना मसूर लोबिया मटर पालक मेथी भिंडी तोरी कद्दू मूली चुकंदर पत्तागोभी सलाद सरसों बैगन टुकड़ा मात्रा वज़न आकार लंबाई चौड़ाई ऊँचाई क्षेत्रफल मोटाई लीटर किलो ग्राम मीटर किलोमीटर सेंटीमीटर मिलीमीटर डिग्री कोण आयत त्रिभुज वृत्त वर्ग तारा आकृति रेखा वक्र बिंदु केंद्र आधा चौथाई तिहाई पाँचवाँ दसवाँ गुना दोगुना पैमाना अनुपात दर औसत योग गुणनफल अंतर परिणाम गुणक भागफल गुणा जोड़ घटाव घात वर्गमूल समीकरण सूत्र प्रतीक अक्षर अंक संख्या क्रम अनुक्रम शृंखला सूची तालिका चार्ट ग्राफ आँकड़े संभावना प्रमेय प्रमाण तथ्य त्रुटि सही सन्निकटन परिभाषा शब्दावली अवधारणा वर्गीकरण तुलना विश्लेषण संश्लेषण निष्कर्ष आगमन परिकल्पना प्रायोगिक सैद्धांतिक व्यावहारिक कपड़ा ऊन रेशम सूती जूट चमड़ा रबड़ प्लास्टिक काँच धातु लोहा इस्पात ताँबा पीतल एल्यूमीनियम सोना चाँदी हीरा मोती पन्ना नीलम माणिक ज़मरूद कंकड़ बजरी रेत मिट्टी चिकनीमिट्टी सीमेंट ईंट पत्थर संगमरमर ग्रेनाइट बलुआ चूना खड़िया जिप्सम अभ्रक तेल कोयला गैस बिजली परमाणु सौर पवन जल भूताप ज्वार बायोमास जैव ईंधन कचरा प्रदूषण पुनर्चक्रण अपशिष्ट उत्सर्जन कार्बन ऑक्सीजन हाइड्रोजन नाइट्रोजन हीलियम ओज़ोन वायुमंडल जलमंडल स्थलमंडल जीवमंडल पारिस्थितिकी खाद्यजाल खाद्यशृंखला उत्पादक उपभोक्ता अपघटक परजीवी सहजीवी शिकारी शिकार प्रवासी स्थानीय विलुप्त संकटग्रस्त संरक्षित उद्यान अभयारण्य राष्ट्रीयउद्यान वनस्पतिउद्यान चिड़ियाघर मछलीघर संग्रहालय प्रदर्शनी मेला त्योहार उत्सव परंपरा रीति रिवाज़ संस्कार विवाह जन्म मृत्यु अंतिम संस्कार श्राद्ध पूजा प्रार्थना ध्यान योग आसन प्राणायाम मंत्र भजन कीर्तन आरती प्रसाद तीर्थ यात्रा दर्शन मंदिर मस्जिद गिरजाघर गुरुद्वारा`,

japaneseWords: `tabemono nomimono kudamono yasai niku gyuuniku butaniku toriniku hitsuji uo tamago bata chiizu miruku komugi kome mugi sobamugi toumorokoshi daizu azuki goma rakkase kurumi arumond piano gitaa baiolin doramu furuto sakusofon toranpetto kurarinetto oboe fagotto chero kontrabasu hapu orugan shinseisaiza shikisha gasshoudan ookesutora konsaato raibu rifainaru ensoukai happyoukai bunkasai taiikusai undoukai ensoku shuuryoushiki sotsugyoushiki nyuugakushiki shimegyoushiki kaikaishiki hekaishiki gishiki shiki gyouji nenjuu gyouji tsukinami nichijou isshuukan ikkagetsu ikkanenkan mikkakan futsukakan ichinichijuu gozen gogo shougo mayonaka shinya yuugata tasogare akebono yoake hinode hinori yuuhi iriguchi deguchi higashiguchi nishiguchi minamiguchi kitaguchi chuuou kaisatsuguchi noriba purattofoomu senro hoomu kippu teikiken kaisu jousyaken tokukyuuken shiteiseki jiyuuseki guriinsha ippansha shindigai roudou saigai jiko jishin tsunami taifuu bouseki shoukai gokai kyoudou kinyuu hoken yubin takuhai souryou nimotsu kozutsumi tegami hagaki fuurin fuutou kitte chokin tsuchou tsuuryou kessai shiharai nounyu kurikoshi funnyuu yokin furikomi kariire hensai tanpo hoshounin rishi gankin manki kikan jigyo jigyou shoubai keiei soshiki kouzou saihen gappei bunsha baishuu torihiki shouken torihikijo kabunushi haitou rieki shisan fusai shourai keikaku yosan mitsu mitsumori seikyuusho nouhinsho uketori ryoushusho meisaisho keiyaku yakkan kiyaku kitei kisoku jourei jouken jikan taimu sukedjuuru appu gureedo rankingu reberu suteppu meido hando fasuto sekando saado raundo riigu tornamento chanpion medaru torofii hai shou katsu makeru hikiwake senshu kantoku kouchi maneejaa sapoota fan ouen kappu waarudokappu orinpikku`,

koreanWords: `나무 잎 꽃 열매 씨앗 뿌리 줄기 가지 껍질 풀 밀 보리 쌀 옥수수 콩 깨 땅콩 호두 아몬드 잣 밤 대추 감 배 사과 포도 딸기 수박 복숭아 귤 바나나 오렌지 레몬 자몽 키위 망고 파인애플 코코넛 아보카도 올리브 체리 블루베리 라즈베리 크랜베리 무화과 석류 살구 매실 자두 앵두 밤 도토리 은행 잣 치커리 알로에 라벤더 로즈마리 바질 타임 오레가노 파슬리 딜 세이지 민트 고수 계피 생강 강황 겨자 후추 고춧가루 소금 설탕 식초 간장 된장 고추장 참기름 들기름 올리브유 버터 마가린 마요네즈 케첩 머스터드 소스 드레싱 양념장 젓갈 김치 깍두기 동치미 장아찌 피클 잼 꿀 시럽 초콜릿 캐러멜 바닐라 시나몬 카카오 말차 녹차 홍차 보이차 우롱차 허브차 루이보스차 재스민차 캐모마일차 페퍼민트차 레몬그라스 생강차 유자차 대추차 매실차 옥수수차 보리차 현미차 둥굴레차 결명자차 감잎차 알코올 도수 양조 증류 발효 숙성 블렌딩 디캔팅 스카치 버번 브랜디 꼬냑 보드카 진 럼 데킬라 압생트 리큐르 칵테일 에일 라거 스타우트 포터 필스너 바이젠 돈켈 복 사케 막걸리 동동주 과실주 매실주 청하 산사춘 안동소주 이강주 문배주 과하주 국화주 진달래주 대나무술 미림 요리술 소스 수프 스튜 스프레드 페이스트 브로스 글레이즈 마리네이드 바베큐 그릴 오븐 전자레인지 프라이팬 냄비 솥 찜기 압력밥솥 믹서기 블렌더 주서기 커피머신 토스터 오븐토스터 에어프라이어 식기세척기 냉장고 냉동고 김치냉장고 정수기 전기포트 인덕션 가스레인지 후드 싱크대 도마 칼 가위 국자 뒤집개 집게 거품기 절구 맷돌 체 계량컵 계량스푼 타이머 앞치마 장갑 행주 수세미 세제 표백제 유연제 섬유탈취제 식기세제 주방세제 락스 알코올 살균제 항균제 핸드워시 바디워시 샴푸 컨디셔너 린스 트리트먼트 에센스 세럼 로션 크림 선크림 클렌저 토너 미스트 팩 마스크 스크럽 필링 각질제거제 보습제 영양크림 아이크림 립밤 핸드크림 바디로션 향수 디퓨저 캔들 방향제 세탁기 건조기 다리미 재봉틀 옷걸이 옷장 서랍장 신발장 거울 화장대`,

chinesePinyinWords: `shu ye hua guo zhongzi gen jing zhi shupi cao xiaomai damai dami yumi dadou lüdou hongdou zhima huasheng hetao xingren songren lizhi banli dazao shizi li pingguo putao caomei xigua taozi juzi xiangjiao chengzi ningmeng youzi mihoutao mangguo boluo yezi niuyouguo ganlan yingtao lanmei fumpenzi mangyue wuhuaguo shiliu xing meizi lizi yingtao banli tuzi yinxing songzi cha jiqili luhui xunyicao midie luolemei luo bailijian ou shuyejin bohe xiangcai guipi shengjiang jianghuang jiemod hujiao jiaolufen yan tang cu jiangyou doubanjiang zhima xianggyou ganlanyou huangyou renzaohuangyou tianmianjiang lajiao xiangliaiog haoyou laoyou xiaozhanjiu zhilajian zhenzhuzhen mianqian quanqiu huanjing wuran zaihai shengtai baohu huishou chuli jianzhu sheji zhuangxiu zhuangshi cailiao shuini zhuankuai gangcai mucai boli suliao xiangpi pigepi zhipin fangzhipin mianhua sichou yangmao yama nideng piancai shitou dali huaganyan sayan shihuishi shigao yunmu tie gang tong huangtong lv jin yin zuanshi zhenzhu feibi lanbaoshi hongbaoshi zumuld shali shazi nitu niantu simengtu zhuan shitou fangjia wuding wuzi loufang bieshu gongyu sushe danyuan menkou loutidao yanggtai yushi chasuo woshi keting shufang canting chufang xiyishi cesuo chufang yangtai chelang louti dianti guandao dianxian kaiguan chazuo diaodeng risbi deng fenshan kongtiao nuanqi reshui reshuiqi xiyiji hongganji dianfanguo weibolü diancibl kaoxiang bingxiang bingui xiaodugui zhazhiji zhapji jiabanqi dianrechubei shuhu kafeihu fandian zhongguo koushuiguoji dianfanbao jiandao caidao caibian guozi chuguo zhengguo shaoguo pingdiguo shao caibei liangbei liangshao jishiqi weiqun shoutao mabu baijie xidiji qingjieji jidanshui piaobaji zhenbanqi suanliaoji niunajiu zhurou yangrou niurou jirou yarou erou turou lurou marou barou niaojou jidan yadan edan anchundan quendan xiadanyu haican longxia pangxie youyu zhangyuyu haishen haizhexia hailu haidai zicai jiangzi weiyu quanyu shayu huangyu lianyu niyu liyu caoyu jiyu guiyu biemu wugui qingwa pangxie haixing shuimu haiiao shuicao zaoshan shanhu jiacuo zhenzhu haiquan haidun jingyu laohu sha dayu xiaoyu tuanna wushu kongfu taijiquan yongchunquan xingyiquan baguazhang tongbeiquan zuiquan nanquan changquan qigong taichi wudang shaolin emei kunlun huashan songshan wudangshan emeishan jiuhuashan putoushan beishan zhangshan lushan huangshan taishan hengshan hengshan songshan`,

finnishWords: `turvallisuus liikenne`,

hungarianWords: `oktatás egészség`,

greekWords: `μεταφορές πληροφοριακή ηλεκτρονική διαδίκτυο πρόγραμμα εφαρμογή λογισμικό υλικό δίκτυο βάση`,

ukrainianWords: `транспорт наука освіта`,

bulgarianWords: `транспорт наука образование здравеопазване околна среда земеделие промишленост туризъм сигурност икономика енергия търговия правосъдие`,

croatianWords: `znanost edukacija zdravstvo transport okoliš poljoprivreda turizam sigurnost energija`,

serbianWords: `наука образовање здравство транспорт безбедност енергија трговина правда слобода`,

slovakWords: `doprava bezpečnosť energetika obchod spravodlivosť sloboda`,

slovenianWords: `promet varnost energetika trgovina pravičnost svoboda demokracija napredek znanost izobraževanje zdravstvo industrija turizem kmetijstvo umetnost`,

estonianWords: `transport ohutus energeetika kaubandus õiglus vabadus demokraatia progress teadus haridus`,

latvianWords: `transports drošība enerģētika tirdzniecība taisnīgums brīvība demokrātija progress zinātne izglītība veselība rūpniecība tūrisms lauksaimniecība`,

lithuanianWords: `transportas saugumas energetika prekyba teisingumas laisvė demokratija pažanga mokslas švietimas sveikata pramonė turizmas žemdirbystė menas`,

thaiWords: `วิทยาศาสตร์ คณิตศาสตร์ ภาษาศาสตร์ เศรษฐศาสตร์ รัฐศาสตร์ นิติศาสตร์ ดาราศาสตร์ สังคมวิทยา จิตวิทยา มานุษยวิทยา ปรัชญา ภูมิศาสตร์ ชีววิทยา เคมี ฟิสิกส์ ดนตรี วรรณกรรม จิตรกรรม ประติมากรรม สถาปัตยกรรม วิศวกรรม อุตสาหกรรม`,

indonesianWords: `biologi fisika kimia matematika geografi sejarah seni musik tari lukisan arsitektur hukum ilmu`,

malayWords: `biologi fizik kimia matematik geografi sejarah kesenian muzik tarian lukisan arca sastera drama filem buku`,

filipinoWords: `biyolohiya pisika kimika matematika heograpiya kasaysayan sining musika sayaw pintura iskultura panitikan nobela tula dula pelikula aklat aralin pagsusulit`,

swahiliWords: `biolojia fizikia kemia hesabu jiografia historia sanaa muziki densi uchoraji uchongaji fasihi riwaya ushairi tamthilia filamu televisheni redio gazeti jarida kitabu maktaba shule chuo kikuu mwalimu mwanafunzi mtihani darasa maabara utafiti`,

hebrewWords: `ביולוגיה פיזיקה כימיה מתמטיקה גיאוגרפיה היסטוריה אמנות מוזיקה ריקוד ציור פיסול ספרות שירה רומן מחזה`,

persianWords: `ریاضیات جغرافیا تاریخ فناوری صنعت کشاورزی بازرگانی آزادی`,

urduWords: `ریاضی جغرافیہ تاریخ فناوری صنعت زراعت بازرگانی آزادی سائنس ٹیکنالوجی حکومت تعلیم صحت ثقافت فنون کھیل سیاحت قانون فلسفہ ادب شاعری موسیقی`,

bengaliWords: `গণিত ভূগোল ইতিহাস প্রযুক্তি শিল্প কৃষি বাণিজ্য স্বাধীনতা বিজ্ঞান সরকার শিক্ষা স্বাস্থ্য সংস্কৃতি কলা খেলা পর্যটন আইন দর্শন সাহিত্য কবিতা সঙ্গীত চিত্র ভাস্কর্য নাটক সিনেমা টেলিভিশন বেতার সংবাদ পত্র বই পাঠশালা মহাবিদ্যালয়`,

tamilWords: `கணிதம் புவியியல் வரலாறு தொழில்நுட்பம் தொழில் வேளாண்மை வணிகம் சுதந்திரம் அறிவியல் அரசு கல்வி சுகாதாரம் பண்பாடு கலை விளையாட்டு சுற்றுலா சட்டம் தத்துவம் இலக்கியம் கவிதை இசை ஓவியம் சிற்பம் நாடகம் திரைப்படம் தொலைக்காட்சி வானொலி செய்தி புத்தகம் பள்ளி கல்லூரி பல்கலைக்கழகம் ஆசிரியர் மாணவர் தேர்வு ஆராய்ச்சி பேராசிரியர் நிர்வாகம் திட்டம் கொள்கை உரிமை கடமை நீதி மன்றம் வழக்கறிஞர் நீதிபதி`,

teluguWords: `గణితం భూగోళం చరిత్ర సాంకేతికత పరిశ్రమ వ్యవసాయం వాణిజ్యం స్వాతంత్ర్యం శాస్త్రం ప్రభుత్వం విద్య ఆరోగ్యం సంస్కృతి కళ క్రీడ పర్యాటకం చట్టం తత్వశాస్త్రం సాహిత్యం కవిత్వం సంగీతం చిత్రకళ శిల్పకళ నాటకం సినిమా టెలివిజన్ రేడియో వార్త పుస్తకం పాఠశాల కళాశాల విశ్వవిద్యాలయం ఉపాధ్యాయుడు విద్యార్థి పరీక్ష పరిశోధన ఆచార్యుడు నిర్వహణ ప్రణాళిక విధానం హక్కు బాధ్యత న్యాయం సభ`,

basqueWords: `zientzia natura biologia fisika kimika matematika historia artea musika dantza pintura eskultura literatura poesia nobela antzerkia zinema telebista irratia`
};

function expandLanguage(result, varName, addWords) {
    const words = addWords.trim().split(/\s+/).filter(w => w.length > 0);
    if (words.length === 0) return result;

    const searchStr = `private val ${varName}`;
    const startIdx = result.indexOf(searchStr);
    if (startIdx === -1) {
        console.log(`WARNING: Could not find ${varName}`);
        return result;
    }

    const closingStr = '    )}';
    let closingIdx = result.indexOf(closingStr, startIdx);
    if (closingIdx === -1) {
        console.log(`WARNING: Could not find closing for ${varName}`);
        return result;
    }

    const section = result.substring(startIdx, closingIdx + closingStr.length);
    const existingWords = new Set();
    const strMatches = [...section.matchAll(/"([^"]*)"/g)];
    for (const m of strMatches) {
        m[1].split(/\s+/).filter(w => w.length > 0).forEach(w => existingWords.add(w));
    }

    const newWords = words.filter(w => !existingWords.has(w));
    if (newWords.length === 0) {
        console.log(`${varName}: all ${words.length} words already exist, skipping`);
        return result;
    }

    const lines = [];
    for (let i = 0; i < newWords.length; i += 15) {
        const chunk = newWords.slice(i, i + 15).join(' ');
        lines.push(`        "${chunk} "`);
    }
    const insertion = ' +\n' + lines.join(' +\n');

    const beforeClosing = result.substring(startIdx, closingIdx);
    const lastQuoteIdx = beforeClosing.lastIndexOf('"');
    if (lastQuoteIdx === -1) return result;
    const absoluteQuoteIdx = startIdx + lastQuoteIdx;

    result = result.substring(0, absoluteQuoteIdx + 1) + insertion + result.substring(absoluteQuoteIdx + 1);
    console.log(`${varName}: added ${newWords.length} new words (${words.length - newWords.length} duplicates removed)`);
    return result;
}

let result = content;
for (const [varName, addWords] of Object.entries(additions)) {
    result = expandLanguage(result, varName, addWords);
}

fs.writeFileSync(filePath, result, 'utf8');
console.log('\nDone! Round 3.');
