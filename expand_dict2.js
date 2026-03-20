const fs = require('fs');
const filePath = 'app/src/main/java/com/deltavoice/PredictiveWordList.kt';
let content = fs.readFileSync(filePath, 'utf8');

const additions = {

arabicWords: `حكومة تعليم مجتمع تطور صناعة زراعة سياحة رياضة ثقافة فنون تقنية علوم تجارة حرية عدل تقدم أمان نقل مواصلات هندسة حقوق فلسفة أدب شعر علاج بناء تصميم برمجة حساب نفس علم اجتماع لغة ترجمة رسم نحت تصوير مسرح موسيقى رقص غناء طهي خياطة حياكة نجارة حدادة سباكة كهرباء ميكانيكا إلكترونيات اتصالات إعلام صحافة بث نشر طباعة تحرير كتابة قراءة ترجمة بحث دراسة تدريس تعلم امتحان شهادة جامعة كلية معهد أكاديمية مختبر مكتبة قاعة فصل درس محاضرة ندوة مؤتمر ورشة تدريب خبرة مهارة كفاءة إبداع ابتكار اختراع اكتشاف تجربة نظرية فرضية قاعدة مبدأ أساس منهج أسلوب طريقة وسيلة أداة آلة جهاز معدات مواد خامات إنتاج تصنيع تجميع تعبئة تغليف تخزين توزيع تسويق إعلان دعاية ترويج مبيعات عميل زبون مستهلك مورد مصدر شريك مستثمر رأسمال ربح خسارة ميزانية إيرادات مصروفات ضريبة جمرك تأمين قرض فائدة عملة صرف بورصة سهم سند استثمار ادخار حساب بنكي تحويل سحب إيداع رصيد كشف ديون سداد أقساط رهن عقاري ملكية إيجار عقد اتفاقية شراكة مؤسسة شركة مصنع متجر فرع إدارة قيادة تخطيط تنظيم رقابة متابعة تقييم جودة معيار مواصفة شكوى اقتراح حل مشكلة تحدي فرصة نقطة ضعف قوة خطة هدف رؤية رسالة قيمة سياسة إجراء لائحة نظام قرار تنفيذ تطبيق مراجعة تعديل تحسين تطوير صيانة إصلاح تجديد تحديث توسع نمو انكماش ركود تضخم بطالة فقر ثروة رخاء رفاهية سعادة صحة مرض علاج وقاية لقاح دواء عملية جراحة تشخيص فحص تحليل أشعة مستشفى عيادة صيدلية طوارئ إسعاف نقاهة شفاء مريض طبيب ممرضة جراح صيدلي مختص استشاري`,

hindiWords: `सरकार समाज विकास उद्योग कृषि पर्यटन संस्कृति कला प्रौद्योगिकी विज्ञान व्यापार न्याय प्रगति सुरक्षा परिवहन इंजीनियरिंग अधिकार दर्शन साहित्य कविता चिकित्सा निर्माण डिज़ाइन प्रोग्रामिंग लेखा गणित भूगोल भौतिकी रसायन जीवविज्ञान भाषा अनुवाद चित्रकला मूर्तिकला नाटक नृत्य संगीत खाना सिलाई बुनाई बढ़ईगीरी लोहारी नलसाजी बिजली यांत्रिकी इलेक्ट्रॉनिक्स संचार मीडिया प्रसारण प्रकाशन मुद्रण संपादन परीक्षा प्रमाणपत्र विश्वविद्यालय कॉलेज संस्थान अकादमी प्रयोगशाला पुस्तकालय कक्षा पाठ व्याख्यान संगोष्ठी सम्मेलन कार्यशाला प्रशिक्षण अनुभव कौशल दक्षता रचनात्मकता नवाचार आविष्कार खोज प्रयोग सिद्धांत परिकल्पना नियम सिद्धांत आधार विधि शैली साधन उपकरण यंत्र मशीन सामग्री उत्पादन विनिर्माण संयोजन पैकेजिंग भंडारण वितरण विपणन विज्ञापन प्रचार बिक्री ग्राहक उपभोक्ता आपूर्तिकर्ता साझेदार निवेशक पूंजी लाभ हानि बजट राजस्व व्यय कर सीमाशुल्क बीमा ऋण ब्याज मुद्रा विनिमय शेयर बांड निवेश बचत खाता बैंक हस्तांतरण निकासी जमा शेष ऋणपत्र किस्त बंधक स्वामित्व किराया अनुबंध साझेदारी संस्था कारखाना शाखा प्रबंधन नेतृत्व योजना संगठन निगरानी मूल्यांकन गुणवत्ता मानक विनिर्देश शिकायत सुझाव समाधान चुनौती अवसर कमज़ोरी लक्ष्य दृष्टि संदेश मूल्य नीति प्रक्रिया नियमावली प्रणाली निर्णय कार्यान्वयन समीक्षा संशोधन सुधार रखरखाव मरम्मत नवीनीकरण विस्तार वृद्धि संकुचन मंदी मुद्रास्फीति बेरोज़गारी गरीबी धन समृद्धि कल्याण रोग उपचार रोकथाम टीका शल्यचिकित्सा निदान जाँच विश्लेषण एक्सरे अस्पताल चिकित्सालय दवाखाना आपातकाल एम्बुलेंस उपचार मरीज़ शल्यचिकित्सक विशेषज्ञ परामर्शदाता आकाश धरती तारा ग्रह चंद्रमा सूर्य ब्रह्मांड आकाशगंगा उल्कापिंड धूमकेतु ग्रहण कक्षा उपग्रह अंतरिक्ष राकेट यान अंतरिक्षयात्री दूरबीन वेधशाला प्रकाश ऊर्जा गुरुत्वाकर्षण चुंबकत्व विद्युत ध्वनि तरंग कण परमाणु अणु इलेक्ट्रॉन प्रोटॉन न्यूट्रॉन रासायनिक यौगिक तत्व मिश्रण विलयन अम्ल क्षार लवण गैस द्रव ठोस तापमान दबाव आयतन घनत्व गति वेग त्वरण बल कार्य शक्ति दूरी समय चाल कोण त्रिभुज वर्ग आयत वृत्त गोला बेलन शंकु`,

japaneseWords: `tabemono nomimono ryouri kaimono shigoto undou benkyou sanpo ryokou kekkon sotsugyou tanjoubi kurisumasu shogatsu matsuri hanami enkai kaigi shiken mensetsu densha chikatetsu shinkansen takushii fune herikoputa jidousha torakku ootobai jitensha hobaa suki naiyou komochi ooku sukunai motto issho betsu nai atarashii furui nagai mijikai takai hikui ookii chiisai samui atsui warui hayai osoi muzukashii yasashii omoi karui suki kirai jiyuu heiwa sensou heisei shouri haiboku byoudou sabetsu kiken anzen shizen kankyou osen hakai hogo hozon saisei enerugi shigen mottainai risaikuru gomi bunri shori mondai kaiketsu genin kekka eikyou kanren soudan teian iken sansei hantai giron setsumei shoukai hiyou nedan keisan waribiki muryou yuushuutsu yunyuu boeki keizai kabushiki kabu bukka keiki fukyo koukei chingin kyuuryou shunyu shotoku zeikin hoken nenkin taishoku shushoku tenshoku mensetsu rirekisho shikaku keiken jisseki seiseki seichoo mokuhyou keikaku yotei sukeju-ru shimekiri gijutsu nouryoku chishiki gakumon kenkyuu kaihatsu hatsumei hakken shinpo kakushin dentou bunka rekishi shakai seiji keizai hou kenri gimu sekinin jiyuu byoudou jinken heiwa kyouryoku kyousou kyouiku gakkou daigaku senmon koutougakkou shougakkou chuugakkou youchien hoikuen jugyou jikanwari shukudai shiken tesuto seiseki sotsugyo nyuugaku gakuhi shoukingaku ronbun hakase kyouju kouchou kyoushi sensei seito gakusei kohai senpai doukyusei tomodachi nakama shinyuu koibito kareshi kanojo kekkon rikon konyaku shinkon fuufu kazoku ryoushin oyako kyoudai shimai itoko meikko oi shinseki shinzoku sosen kodomo shounen shoujo wakamono seinen otona roujin dansei josei ninshin shussan ikuji youji jidou seichouiki seishun chuunen rounen jinsei unmei kankei ningen shakai seikatsu nichijou shuumatsu kyuujitsu yasumi shucchou ryokou shutchou kaigai kokunai chihou inaka toshi miyako tokai kougen kaigan hantou tairiku hokubu nanbu toubu seibu chuuou hoppou nanpou nantou hokutou seinan kaigai`,

koreanWords: `음악 영화 드라마 소설 시 그림 조각 사진 뉴스 방송 신문 잡지 인터넷 소셜미디어 블로그 앱 프로그램 소프트웨어 하드웨어 네트워크 서버 데이터 정보 기술 과학 수학 물리 화학 생물 지리 역사 정치 경제 법률 철학 심리학 사회학 교육학 의학 약학 건축학 공학 언어학 문학 예술 음악학 체육 종교 농업 어업 임업 광업 제조업 건설업 무역 금융 보험 부동산 운송 통신 서비스 정부 군대 경찰 소방 외교 국방 세금 예산 복지 연금 보건 환경 에너지 자원 기후 날씨 온도 습도 바람 구름 비 눈 태풍 지진 홍수 가뭄 산불 안개 서리 우박 번개 천둥 무지개 이슬 봄 여름 가을 겨울 계절 자연 숲 나무 꽃 풀 열매 씨앗 뿌리 줄기 잎사귀 가지 껍질 고양이 강아지 토끼 거북 금붕어 앵무새 햄스터 기니피그 도마뱀 이구아나 페럿 소 돼지 닭 오리 거위 칠면조 염소 사슴 여우 늑대 너구리 다람쥐 고슴도치 두더지 박쥐 올빼미 매 학 백조 까치 비둘기 딱따구리 제비 앵무 개미 벌 나비 잠자리 매미 귀뚜라미 반딧불이 사마귀 무당벌레 메뚜기 거미 지렁이 달팽이 장수풍뎅이 꿀벌 말벌 모기 파리 바퀴벌레 진드기 음식 요리 볶음 찜 구이 튀김 조림 무침 회 죽 빵 떡 면 국 찌개 탕 전 볶음밥 잡채 냉면 콩나물 시금치 부추 미나리 깻잎 상추 양배추 브로콜리 파프리카 호박 가지 버섯 옥수수 고구마 감 밤 대추 잣 호두 아몬드 땅콩 해바라기씨 참깨 들깨 미역 다시마 김 멸치 젓갈 장아찌 깍두기 동치미 백김치 열무김치 총각김치 양념 고춧가루 후추 생강 겨자 참기름 들기름 식용유 맛소금 물엿 꿀 잼 마요네즈 케첩 머스터드 초콜릿 사탕 젤리 건빵 떡볶이 순대 어묵 붕어빵 호떡 핫도그 치킨 피자 햄버거 짜장면 짬뽕 탕수육 만두 춘권 초밥 라멘 돈가스 우동 카레 샐러드 스테이크 파스타 리조또 샌드위치 와플 팬케이크 도넛 마카롱 크로와상 머핀 브라우니 타르트 푸딩 요거트 아이스크림 셔벗 시럽 크림 생수 탄산수 이온음료 에너지음료 녹차 홍차 보리차 옥수수차 식혜 수정과 매실차 대추차 생강차 유자차 레모네이드 스무디 밀크셰이크 카페라떼 카푸치노 에스프레소 아메리카노 모카 핫초코 두유 과일주스 오렌지주스 사과주스 포도주스 토마토주스 청주 막걸리 맥주 와인 소주 위스키 보드카 럼 진 브랜디 칵테일 샴페인 건강 질병 감기 독감 기침 열 두통 복통 설사 구토 알레르기 천식 당뇨병 고혈압 심장병 암 폐렴 위염 관절염 피부염 결막염 중이염 편도선염 방광염 빈혈 골절 탈구 타박상 화상 동상 멍 흉터 수술 진료 처방 약 주사 입원 퇴원 검진 예방접종 응급실 구급차 산소 혈압 체온 맥박 호흡 혈액 소변 엑스레이 초음파 내시경`,

chinesePinyinWords: `yinyue dianying dianshiju xiaoshuo shi huihua diaosu zhaopian xinwen guangbo baozhi zazhi hulianwang shejiaomeiti boke yingyong ruanjian yingjian wangluo fuwuqi shuju xinxi jishu kexue shuxue wuli huaxue shengwu dili lishi zhengzhi jingji falv zhexue xinlixue shehuixue jiaoyuxue yixue yaoxue jianzhuxue gongcheng yuyanxue wenxue yishu yinyuexue tiyu zongjiao nongye yuye linye kuangye zhizaoye jianzhuye maoyi jinrong baoxian fangdichan yunshu tongxin fuwu zhengfu jundui jingcha xiaofang waijiao guofang shuishou yusuan fuli yanglaojin weisheng huanjing nengyuan ziyuan qihou tianqi wendu shidu feng yun yu xue taifeng dizhen hongshui ganhan shanhuo dawu shuang bingbao shandian lei caihong lu chun xia qiu dong jijie ziran senlin shu hua cao guoshi zhongzi gen jing yepian zhi shupi mao gou tuzi wugui jinyu yingwu cangshu tuolong xunyang bao zhu ji yazi e huoji shanyang lu huli lang huanxiong songshu ciwei yanshu bianfu maotouying jiu ying he tianre xique zhuomuniao yanzi mayi mifeng hudie qingting chan xishuai yinghuochong tanglang piaochong mazha zhizhu qiuyin woniu jiachong fengmi mafeng wenzi cangying zhanglanng pianduichongxiao chi shao kao zha dun liang hui zhou mian tang chao zhimafan zamu lengmian douya bocai jiucai qincai zhimaye shengcai juanxincai xilan caijiao nangua qiezi mogu yumi digua shizi lizi dazao songren hutaoren xingren huasheng kuazishi zhima haidai zicai hai xiaoyu jiangcai suancai paocai tiaoliao jiaolufen hujiao jiang jiemod xianggyou zhiwuyou weiijing tanjiang fengmi guojiang jiangyou cula shajiang huajiao rougui dingxiang bajiao xiangye yancao shenghuo jiating gongzuo xuexiao yiyuan shangdian canting kafeidian jiuba jiulou chaoshi shuchang caichangshichang yeshijie shangye zhongxin bangongshi gongchang cangku tingchechang jiayouzhan xichechang lifa meirongyuan xiyidian ganxidian xiuxiedian wenjudian shudian huadadian jiajudian dianqidian fuzhuangdian zhubaodan yaobaodian wanjudian tiyuqidian chongwudian yuanlinyishu jianshen yujia taijiquan wushu pashan qiche bisai youyong huabing huaxue tiaoqiao paola jianshencao zixingche lanqiu zuqiu paiqiu yumaoqiu pingpangqiu wangqiu bangqiu gaolfu baolingqiu jijian sheji qima shejian jiudao quanji shuaijiao juzhong tiancao tiaoyuan tiaoqiao touqiu duanpao changpao jieli saiche jiqiren wurenji rengong zhineng dashuju yuanjisuan wulianwang`,

finnishWords: `turvallisuus liikenne koulutus terveys tiede talous`,

hungarianWords: `oktatás egészség tudomány kultúra energia kereskedelem`,

greekWords: `μεταφορά πληθυσμός μέσα ερώτηση αλλαγή μέρος πράγμα νερό λόγος πληροφορία ομάδα λέξη πρόβλημα ψηλός χαμηλός`,

ukrainianWords: `транспорт освіта здоров'я наука культура торгівля`,

bulgarianWords: `наука образование здравеопазване транспорт околна среда земеделие промишленост туризъм`,

croatianWords: `znanost edukacija zdravstvo prijevoz okoliš poljoprivreda industrija turizam sredstvo pogon radionica`,

serbianWords: `наука образовање здравство превоз околина пољопривреда индустрија туризам средство погон радионица производња управљање планирање извоз увоз буџет порез приход расход зарада плата инвестиција камата дуг кредит осигурање`,

slovakWords: `veda vzdelanie zdravotníctvo doprava životné prostredie poľnohospodárstvo priemysel cestovný ruch nástroj zariadenie dielňa výroba riadenie plánovanie vývoz dovoz rozpočet`,

slovenianWords: `znanost izobraževanje zdravstvo prevoz okolje kmetijstvo industrija turizem sredstvo pogon delavnica proizvodnja upravljanje načrtovanje izvoz uvoz proračun davek dohodek odhodek plača investicija obresti dolg posojilo zavarovanje`,

estonianWords: `teadus haridus tervishoid transport keskkond põllumajandus tööstus turism vahend seade töökoda tootmine juhtimine planeerimine`,

latvianWords: `zinātne izglītība veselības aprūpe transports apkārtējā vide lauksaimniecība rūpniecība tūrisms instruments ierīce darbnīca ražošana vadība plānošana eksports imports budžets nodoklis`,

lithuanianWords: `mokslas švietimas sveikatos priežiūra transportas aplinkos apsauga žemės ūkis pramonė turizmas priemonė įrenginys dirbtuvė gamyba valdymas planavimas eksportas importas biudžetas mokestis pajamos išlaidos`,

thaiWords: `การเกษตร การประมง การป่าไม้ การทำเหมือง การผลิต การก่อสร้าง การค้าระหว่างประเทศ การเงิน การประกันภัย อสังหาริมทรัพย์ การขนส่ง การสื่อสาร การบริการ ทหาร ตำรวจ ดับเพลิง การทูต กลาโหม ภาษี งบประมาณ สวัสดิการ บำนาญ สาธารณสุข สิ่งแวดล้อม พลังงาน ทรัพยากร ภูมิอากาศ อุณหภูมิ ความชื้น พายุไต้ฝุ่น แผ่นดินไหว น้ำท่วม ภัยแล้ง ไฟป่า หมอก น้ำค้างแข็ง ลูกเห็บ ฟ้าผ่า สายรุ้ง`,

indonesianWords: `ilmu alam biologi fisika kimia matematika geografi sejarah seni musik tari lukisan`,

malayWords: `ilmu alam biologi fizik kimia matematik geografi sejarah kesenian muzik tarian lukisan arca kraftangan sastera novel puisi drama filem televisyen radio akhbar majalah buku perpustakaan`,

filipinoWords: `agham kalikasan biyolohiya pisika kimika matematika heograpiya kasaysayan sining musika sayaw pintura iskultura panitikan nobela tula dula pelikula telebisyon radyo dyaryo magasin aklat silid-aklatan`,

swahiliWords: `sayansi asili biolojia fizikia kemia hesabu jiografia historia sanaa muziki densi uchoraji uchongaji fasihi riwaya ushairi tamthilia filamu televisheni redio gazeti jarida kitabu maktaba shule chuo`,

hebrewWords: `מדע טבע ביולוגיה פיזיקה כימיה מתמטיקה גיאוגרפיה היסטוריה אמנות מוזיקה ריקוד ציור פיסול ספרות שירה`,

persianWords: `علم طبیعت زیست فیزیک شیمی ریاضیات جغرافیا تاریخ هنر نگارگری رقص نقاشی`,

urduWords: `علم فطرت حیاتیات طبیعیات کیمیا ریاضی جغرافیہ تاریخ فنون مصوری رقص نقاشی مجسمہ ادب شاعری ناول ڈرامہ فلم ٹیلی ویژن ریڈیو اخبار رسالہ کتاب کتب خانہ اسکول کالج`,

bengaliWords: `বিজ্ঞান প্রকৃতি জীববিদ্যা পদার্থবিদ্যা রসায়ন গণিত ভূগোল ইতিহাস শিল্পকলা সংগীত নৃত্য চিত্রকলা ভাস্কর্য সাহিত্য কবিতা উপন্যাস নাটক চলচ্চিত্র টেলিভিশন বেতার সংবাদপত্র পত্রিকা বই গ্রন্থাগার বিদ্যালয় মহাবিদ্যালয় বিশ্ববিদ্যালয় শিক্ষক ছাত্র পরীক্ষা পাঠ্যপুস্তক শ্রেণীকক্ষ পাঠাগার গবেষণা সন্দর্ভ প্রবন্ধ গবেষক অধ্যাপক উপাচার্য সভাপতি সচিব`,

tamilWords: `அறிவியல் இயற்கை உயிரியல் இயற்பியல் வேதியியல் கணிதம் புவியியல் வரலாறு கலை இசை நடனம் ஓவியம் சிற்பம் இலக்கியம் கவிதை நாவல் நாடகம் திரைப்படம் தொலைக்காட்சி வானொலி செய்தி இதழ் புத்தகம் நூலகம் பள்ளி கல்லூரி ஆசிரியர் மாணவர் தேர்வு பாடநூல் வகுப்பறை ஆராய்ச்சி கட்டுரை ஆய்வாளர் பேராசிரியர் துணைவேந்தர் தலைவர் செயலாளர் உறுப்பினர் குழு சபை மன்றம் அமைப்பு நிறுவனம் நிர்வாகம் திட்டம் கொள்கை விதி சட்டம் உரிமை கடமை பொறுப்பு தண்டனை குற்றம் நீதிமன்றம் வழக்கறிஞர் நீதிபதி தீர்ப்பு சான்று சாட்சி புகார் மேல்முறையீடு ஆணை அறிவிப்பு விண்ணப்பம் அனுமதி தடை நிபந்தனை ஒப்பந்தம் உடன்படிக்கை`,

teluguWords: `శాస్త్రం ప్రకృతి జీవశాస్త్రం భౌతికశాస్త్రం రసాయనశాస్త్రం గణితం భూగోళం చరిత్ర కళ సంగీతం నృత్యం చిత్రకళ శిల్పకళ సాహిత్యం కవిత్వం నవల నాటకం సినిమా టెలివిజన్ రేడియో వార్త పత్రిక పుస్తకం గ్రంథాలయం పాఠశాల కళాశాల ఉపాధ్యాయుడు విద్యార్థి పరీక్ష పాఠ్యపుస్తకం తరగతిగది పరిశోధన వ్యాసం పరిశోధకుడు ఆచార్యుడు ఉపకులపతి అధ్యక్షుడు కార్యదర్శి సభ్యుడు సంఘం సభ వేదిక సంస్థ నిర్వహణ ప్రణాళిక విధానం నియమం చట్టం హక్కు బాధ్యత శిక్ష నేరం న్యాయస్థానం న్యాయవాది న్యాయమూర్తి తీర్పు సాక్ష్యం సాక్షి ఫిర్యాదు అప్పీలు ఆదేశం ప్రకటన దరఖాస్తు అనుమతి నిషేధం షరతు ఒప్పందం`,

basqueWords: `zientzia natura biologia fisika kimika matematika geografia historia artea musika dantza pintura eskultura literatura poesia`
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
    if (lastQuoteIdx === -1) {
        console.log(`WARNING: Could not find last quote for ${varName}`);
        return result;
    }
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
console.log('\nDone! File expanded (round 2).');
