package com.rahedalat.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayDeque;

public class MainActivity extends Activity {
    private GameView game;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(12,16,20));
        getWindow().setNavigationBarColor(Color.rgb(12,16,20));
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(true);
        getWindow().getDecorView().setSystemUiVisibility(0);
        game = new GameView(this);
        setContentView(game);
    }

    @Override public void onBackPressed() {
        if (!game.goBack()) game.askExit();
    }

    @Override protected void onPause() { super.onPause(); if(game!=null)game.pauseMusicForBackground(); }
    @Override protected void onResume() { super.onResume(); if(game!=null)game.resumeMusicAfterBackground(); }

    public static class GameView extends View {
        static final int START=0, OFFICE=1, LIBRARY=2, CASE=3, LESSON=4, QUIZ=5,
                RESULT=6, PROGRESS=7, ARCHIVE=8, LAWBOOK=9,
                CASE_ROOM=10, CASE1_AMIR=11, CASE1_EVIDENCE=13, CASE1_PHONE=14, CASE1_RECEIPTS=15, CASE1_DVD=16;

        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        final ArrayDeque<Integer> history = new ArrayDeque<>();
        final SharedPreferences prefs;

        Bitmap officeBg, libraryBg, lawyer, client, judge, officer;
        Bitmap caseRoomAmir, amirCloseup, amirEvidenceDialogue, evidenceOpen, amirFace, lawyerFace;
        final Bitmap[] receiptPieces = new Bitmap[22];
        final float[] receiptPieceX = new float[22];
        final float[] receiptPieceY = new float[22];
        final float[] receiptTargetX = new float[22];
        final float[] receiptTargetY = new float[22];
        final float[] receiptRot = new float[22];
        final boolean[] receiptOnBoard = new boolean[22];
        final boolean[] receiptLocked = new boolean[22];
        final Bitmap[] dvdFrames = new Bitmap[20];
        int dvdFrameIndex = 0;
        int selectedReceiptPiece = -1;
        final int[] trayOrder = new int[22];
        int phonePage = 0; // 0=home, 1=messages, 2=bank transfer
        String phoneInput = "";
        boolean phoneSolved = false, receiptSolved = false, dvdSolved = false;
        MediaPlayer music;
        int screen = START;
        int selectedCase = 1;
        int storyStep = 0;
        boolean settings = false, exitConfirm = false, answerChosen = false, correct = false;
        float ox, oy, scale;
        float roomPan = 0f, roomDownX = 0f, roomStartPan = 0f;
        float amirPan = 0f, amirDownX = 0f, amirStartPan = 0f;
        float evidencePan = 0f, evidenceDownX = 0f, evidenceStartPan = 0f;
        boolean roomDragging = false, amirDragging = false, evidenceDragging = false;
        float roomZoom=1f, roomPanStart=0f, roomTouchStartX=0f, roomTouchStartY=0f; boolean roomPinching=false; float roomPinchStartDistance=0f, roomZoomStart=1f;
        long introStartMs=SystemClock.uptimeMillis(), evidenceNoticeUntil=0L;
        boolean showEvidenceBack=false, receiptDragging=false;
        float receiptDownX=0f,receiptDownY=0f,receiptStartX=0f,receiptStartY=0f;
        int case1Dialogue = 0;

        final String[] titles = {
                "",
                "پرونده قرارداد و امضای مورد اختلاف",
                "پرونده مطالبه وجه و تشخیص طرفین دعوا",
                "پرونده نقص دادخواست و تکمیل اطلاعات",
                "پرونده مطالبه خسارت و تعیین خواسته",
                "پرونده سند و اثبات ادعا"
        };

        final String[] casePeople = {"", "مریم احمدی", "رضا کریمی", "سارا موسوی", "علی نادری", "نگار حسینی"};
        final String[] caseIssues = {
                "",
                "امضای قراردادی که موکل انتساب آن را نمی‌پذیرد.",
                "مطالبه وجهی که در آن باید خواهان و خوانده درست شناسایی شوند.",
                "دادخواستی که بخشی از اطلاعات ضروری آن ناقص ثبت شده است.",
                "مطالبه خسارت و تشخیص دقیق چیزی که از دادگاه درخواست می‌شود.",
                "اختلاف درباره سند و ارزش آن به عنوان دلیل در دعوا."
        };

        final String[][] story = {
                {},
                {"من قراردادی را امضا نکرده‌ام، اما طرف مقابل نسخه‌ای از قرارداد را ارائه کرده است.",
                 "اول باید بدانیم دعوا از چه مسیری وارد دادگاه شده و چه چیزی از دادگاه خواسته شده است.",
                 "اگر امضا را قبول ندارم، باید ادعای خود را روشن و دلیل‌های موجود را بررسی کنم.",
                 "حالا پرونده آماده است تا ببینیم نخستین اقدام حقوقی درست چیست."},
                {"من از شخصی طلب دارم و می‌خواهم مبلغ بدهی را از راه قانونی مطالبه کنم.",
                 "قبل از هر چیز باید مشخص کنیم چه کسی دعوا را مطرح می‌کند و دعوا علیه چه کسی است.",
                 "پس در این پرونده، عنوان خواهان و خوانده فقط یک اسم نیست؛ نقش هر طرف را مشخص می‌کند.",
                 "حالا باید تصمیم بگیریم در دادخواست چه کسی خواهان و چه کسی خوانده است."},
                {"دادخواست ثبت شده، اما بخشی از اطلاعات آن ناقص است و دفتر دادگاه ایراد گرفته است.",
                 "برای ادامه رسیدگی، باید بدانیم دادخواست چه اطلاعاتی لازم دارد و هر بخش چه کاربردی دارد.",
                 "مشخصات طرفین، خواسته و دلایل از اطلاعاتی هستند که باید با دقت بررسی شوند.",
                 "حالا نوبت توست که تشخیص بدهی کدام اطلاعات برای رفع ایراد مهم‌تر است."},
                {"من خسارتی دیده‌ام و می‌خواهم جبران آن را از دادگاه بخواهم.",
                 "خواسته باید روشن باشد؛ دادگاه باید بداند دقیقاً چه چیزی از او درخواست شده است.",
                 "همچنین در موارد لازم، ارزش خواسته می‌تواند در هزینه دادرسی اثر داشته باشد.",
                 "پس قبل از ادامه پرونده باید خواسته را دقیق و قابل فهم بیان کنیم."},
                {"طرف مقابل می‌گوید سندی دارد که ادعای او را ثابت می‌کند، اما من درباره آن اختلاف دارم.",
                 "اینجا باید مفهوم دلیل را بشناسیم؛ هر ادعا برای اثبات خود به دلیل مناسب نیاز دارد.",
                 "سند یکی از ادله است، اما باید جایگاه و ارتباط آن با ادعا را بررسی کرد.",
                 "اکنون باید تشخیص بدهی دلیل در این پرونده چه نقشی دارد."}
        };

        final String[][] lesson = {
                {},
                {"آموزش پرونده امیر: دادخواست وسیله طرح بسیاری از دعاوی مدنی است. پس از جمع‌آوری و بررسی دلایل، باید خواسته و مشخصات طرفین را روشن و دادخواست را مطابق مقررات به مرجع صالح ارائه کرد.",
                 "نکته آموزشی: دادخواست — نوشته‌ای که خواهان برای طرح دعوا و آغاز رسیدگی، مطابق مقررات به مرجع صالح ارائه می‌کند."},
                {"خواهان کسی است که دعوا را مطرح می‌کند و خوانده کسی است که دعوا علیه او مطرح شده است. تشخیص این دو نقش برای تنظیم درست پرونده ضروری است.",
                 "اصطلاح کلیدی: خواهان و خوانده — دو عنوان ناظر به نقش طرفین در دعوا، نه صرفاً نام اشخاص."},
                {"دادخواست باید اطلاعات لازم درباره اصحاب دعوا، خواسته و جهات و دلایل مورد استناد را در حدود قانون داشته باشد. نقص اطلاعات می‌تواند روند پرونده را متوقف یا اصلاح‌پذیر کند.",
                 "اصطلاح کلیدی: مشخصات اصحاب دعوا — اطلاعاتی که برای شناسایی و ابلاغ به طرفین لازم است."},
                {"خواسته همان چیزی است که خواهان از دادگاه می‌خواهد. روشن بودن خواسته به دادگاه و طرف مقابل کمک می‌کند حدود دعوا مشخص باشد.",
                 "اصطلاح کلیدی: خواسته — نتیجه یا امری که خواهان صدور حکم یا تصمیم درباره آن را از دادگاه درخواست می‌کند."},
                {"دلیل وسیله اثبات یا تقویت ادعای مطرح‌شده در حدود قانون است. سند، اقرار، شهادت و سایر ادله قانونی می‌توانند در اثبات موضوع نقش داشته باشند.",
                 "اصطلاح کلیدی: دلیل — وسیله‌ای که برای اثبات یا تقویت ادعا یا دفاع در چارچوب قانون ارائه می‌شود."}
        };

        final String[] questions = {
                "",
                "برای شروع یک دعوای مدنی، کدام اقدام در این پرونده نقش اصلی دارد؟",
                "در پرونده رضا، چه کسی عنوان خواهان دارد؟",
                "برای رفع ایراد دادخواست، کدام مورد باید با دقت بررسی شود؟",
                "در پرونده خسارت، خواسته به چه چیزی اشاره می‌کند؟",
                "در پرونده نگار، دلیل چه نقشی دارد؟"
        };

        final String[][] options = {
                {},
                {"تقدیم دادخواست از مسیر قانونی", "تماس تلفنی با طرف مقابل", "ارسال یک پیام غیررسمی"},
                {"شخصی که دادخواست علیه او مطرح شده", "شخصی که مطالبه وجه را مطرح کرده", "قاضی رسیدگی‌کننده"},
                {"رنگ جلد پرونده", "اطلاعات ضروری اصحاب دعوا و خواسته و دلایل", "شماره صندلی دادگاه"},
                {"نام ساختمان دادگستری", "زمان مراجعه به دفتر", "چیزی که خواهان از دادگاه درخواست می‌کند"},
                {"تزئین پرونده", "تعیین رنگ فرم دادخواست", "وسیله اثبات یا تقویت ادعا در چارچوب قانون"}
        };

        final int[] correctIndex = {0,0,1,1,2,2};
        final String[] explanations = {
                "",
                "طرح دعوا از مسیر قانونی و تقدیم دادخواست، نقطه شروع این پرونده است.",
                "در این پرونده رضا که مطالبه وجه را مطرح کرده، خواهان است؛ طرف مقابل خوانده است.",
                "مشخصات اصحاب دعوا، خواسته و دلایل از اطلاعات مهم دادخواست هستند و باید دقیق بررسی شوند.",
                "خواسته همان چیزی است که خواهان از دادگاه درخواست می‌کند؛ در اینجا جبران خسارت.",
                "دلیل برای اثبات یا تقویت ادعا در حدود قانون به کار می‌رود و سند یکی از انواع آن است."
        };

        public GameView(Context c) {
            super(c);
            setFocusable(true);
            prefs = c.getSharedPreferences("rah_edalat", Context.MODE_PRIVATE);
            officeBg = load(R.drawable.office_bg);
            libraryBg = load(R.drawable.library_bg);
            lawyer = load(R.drawable.char_lawyer);
            client = load(R.drawable.char_client);
            amirFace = load(R.drawable.amir_face);
            lawyerFace = load(R.drawable.lawyer_face);
            judge = load(R.drawable.char_judge);
            officer = load(R.drawable.char_officer);
            caseRoomAmir = load(R.drawable.case_room_amir);
            amirCloseup = load(R.drawable.case1_amir_closeup);
            amirEvidenceDialogue = load(R.drawable.case1_amir_evidence_dialogue);
            evidenceOpen = load(R.drawable.case1_evidence_open);
            for(int i=0;i<22;i++){
                int id=getResources().getIdentifier(String.format(java.util.Locale.US,"receipt_piece_%02d",i+1),"drawable",getContext().getPackageName());
                receiptPieces[i]=load(id);
            }
            for(int i=0;i<20;i++){
                int id=getResources().getIdentifier(String.format(java.util.Locale.US,"case1_dvd_%02d",i+1),"drawable",getContext().getPackageName());
                dvdFrames[i]=load(id);
            }
            initReceiptPuzzle();
            tp.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        }

        Bitmap load(int id) { return BitmapFactory.decodeResource(getResources(), id); }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w=getWidth(), h=getHeight();
            scale=Math.min(w/1080f,h/2400f);
            ox=(w-1080f*scale)/2f; oy=(h-2400f*scale)/2f;
            c.drawColor(Color.rgb(9,13,17));
            c.save(); c.translate(ox,oy); c.scale(scale,scale);
            if(screen==START) drawStart(c); else {
                drawBackground(c);
                if(screen==OFFICE) drawOffice(c);
                else if(screen==LIBRARY) drawLibrary(c);
                else if(screen==CASE) drawCase(c);
                else if(screen==LESSON) drawLesson(c);
                else if(screen==QUIZ) drawQuiz(c);
                else if(screen==RESULT) drawResult(c);
                else if(screen==PROGRESS) drawProgress(c);
                else if(screen==ARCHIVE) drawArchive(c);
                else if(screen==LAWBOOK) drawLawBook(c);
                else if(screen==CASE_ROOM) drawCaseRoom(c);
                else if(screen==CASE1_AMIR) drawCase1Amir(c);
                else if(screen==CASE1_EVIDENCE) drawCase1Evidence(c);
                else if(screen==CASE1_PHONE) drawCase1Phone(c);
                else if(screen==CASE1_RECEIPTS) drawCase1Receipts(c);
                else if(screen==CASE1_DVD) drawCase1Dvd(c);
                drawTop(c);
            }
            if(settings) drawSettings(c);
            if(exitConfirm) drawExit(c);
            c.restore();
        }

        void drawBackground(Canvas c) {
            if(screen==CASE_ROOM || screen==CASE1_AMIR || screen==CASE1_EVIDENCE || screen==CASE1_PHONE || screen==CASE1_RECEIPTS || screen==CASE1_DVD) return;
            Bitmap b = screen==LIBRARY ? libraryBg : officeBg;
            if(b!=null) c.drawBitmap(b,null,new RectF(0,0,1080,2400),p);
            p.setColor(0x18000000); c.drawRect(0,0,1080,2400,p);
        }

        void drawStart(Canvas c) {
            p.setColor(0xff070d12); c.drawRect(0,0,1080,2400,p);
            if(officeBg!=null){p.setAlpha(55);c.drawBitmap(officeBg,null,new RectF(0,0,1080,2400),p);p.setAlpha(255);}
            long elapsed=SystemClock.uptimeMillis()-introStartMs; float t=Math.min(1f,elapsed/1900f);
            float ease=1f-(float)Math.pow(1f-t,3), pulse=1f+0.025f*(float)Math.sin(elapsed/180f);
            float sc=(0.72f+0.28f*ease)*pulse; int a=(int)(255*Math.min(1f,t*1.5f));
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor((a<<24)|0xffd4a95e);
            c.drawCircle(540,620,175*ease,p);c.drawCircle(540,620,205*ease,p);p.setStyle(Paint.Style.FILL);
            c.save();c.translate(540,650);c.scale(sc,sc);c.translate(-540,-650);
            text(c,"راه عدالت",540,650,92,(a<<24)|Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,900);
            text(c,"یادگیری آیین دادرسی مدنی در دل پرونده",540,790,31,(a<<24)|0xffeadfcd,true,Layout.Alignment.ALIGN_CENTER,850);c.restore();
            if(elapsed>650){float bt=Math.min(1f,(elapsed-650)/700f),be=1f-(float)Math.pow(1f-bt,3);int ba=(int)(255*be);round(c,300,1030,780,1260,34,(ba<<24)|0xff17435d);text(c,"ورود به دفتر وکیل",540,1175,44,(ba<<24)|Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,520);}
            text(c,"ساخته شده توسط پارسا",540,2120,30,0xffbcae98,true,Layout.Alignment.ALIGN_CENTER,650);
            if(elapsed<2300)postInvalidateOnAnimation();
        }

        void drawTop(Canvas c) {
            p.setColor(0x7810181f);c.drawRect(0,80,1080,220,p);
            if(screen!=OFFICE&&screen!=START){round(c,42,92,182,208,34,0xdd18303c);p.setColor(0xfff2eee7);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(120,150,82,150,p);c.drawLine(82,150,103,129,p);c.drawLine(82,150,103,171,p);p.setStyle(Paint.Style.FILL);}
            round(c,898,92,1038,208,34,0xdd18303c);p.setColor(0xfff2eee7);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setStrokeCap(Paint.Cap.ROUND);for(int i=0;i<3;i++){float yy=127+i*23;c.drawLine(936,yy,1000,yy,p);c.drawCircle(952+i*14,yy,5,p);}p.setStyle(Paint.Style.FILL);
            text(c,screenTitle(),540,125,38,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,650);
        }

        String screenTitle(){
            switch(screen){
                case OFFICE:return "دفتر وکیل";
                case LIBRARY:return "کتابخانه پرونده‌ها";
                case CASE:return "پرونده جاری";
                case LESSON:return "یادگیری پرونده";
                case QUIZ:return "تصمیم حقوقی";
                case RESULT:return "نتیجه پرونده";
                case PROGRESS:return "پیشرفت";
                case ARCHIVE:return "بایگانی";
                case LAWBOOK:return "کتاب قانون";
                case CASE_ROOM:return "اتاق رسیدگی پرونده";
                case CASE1_AMIR:return "";
                case CASE1_EVIDENCE:return "محتویات کیف";
                case CASE1_PHONE:return "بررسی گوشی";
                case CASE1_RECEIPTS:return "پازل رسید بانکی";
                case CASE1_DVD:return "بررسی فیلم";
                default:return "راه عدالت";
            }
        }

        void drawOffice(Canvas c) {
            // No visible buttons or labels are placed over office objects.
            // The real objects in the background are the touch targets.
            text(c,"برای تعامل، خودِ اشیای دفتر را لمس کن",540,2310,26,0xfff0e4cf,true,Layout.Alignment.ALIGN_CENTER,850);
        }

        void drawLibrary(Canvas c) {
            text(c,"برای باز کردن پرونده، خودِ پوشه را لمس کن",540,2250,28,0xfff0e4cf,true,Layout.Alignment.ALIGN_CENTER,900);
            for(int i=1;i<=5;i++) drawFolder(c,i,folderX(i),folderY(i),i<=unlocked());
        }

        float folderX(int i){ return 155f + ((i-1)%3)*300f; }
        float folderY(int i){ return 520f + ((i-1)/3)*620f; }

        void drawFolder(Canvas c,int i,float x,float y,boolean open){
            int body=open?0xff7d5a36:0xff4c4b4a;
            round(c,x-92,y-75,x+92,y+85,18,body);
            round(c,x-72,y-93,x+5,y-55,12,body);
            text(c,"پرونده "+toFa(i),x,y-8,25,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,165);
            text(c,"●",x,y+40,22,open?0xffe3bd72:0xff8a8a8a,true,Layout.Alignment.ALIGN_CENTER,60);
        }

        void drawCaseRoom(Canvas c){
            drawRoomScene(c,caseRoomAmir,roomPan,roomZoom);
        }
        void drawRoomScene(Canvas c,Bitmap b,float pan,float zoom){
            if(b==null)return;float top=120f,baseH=2280f,baseW=baseH*b.getWidth()/b.getHeight();float z=Math.max(1f,Math.min(2.2f,zoom));float w=baseW*z,h=baseH*z;float maxX=Math.max(0,w-1080f);float px=Math.max(0,Math.min(pan,maxX));float topY=top-(h-baseH)/2f;c.drawBitmap(b,null,new RectF(-px,topY,w-px,topY+h),p);p.setColor(0x24000000);c.drawRect(0,120,1080,2400,p);
        }
        float roomMaxPan(){if(caseRoomAmir==null)return 0;float h=2280f,w=h*caseRoomAmir.getWidth()/caseRoomAmir.getHeight();return Math.max(0,w*Math.max(1f,Math.min(2.2f,roomZoom))-1080f);}
        void drawCase1Amir(Canvas c){
            Bitmap scene=case1Dialogue==2?amirEvidenceDialogue:caseRoomAmir;drawRoomScene(c,scene,roomPan,roomZoom);
            if(case1Dialogue==0){drawChatMessage(c,amirFace,"من بچمو خیلی دوس دارم، اون متولد ۱۳۹۰ هست و می‌خواستم برای تولدش یه دوچرخه بخرم و پول رو بابت همین انتقال دادم.",620,390,390,980,true);drawChatMessage(c,lawyerFace,"چه اتفاقی افتاده؟",90,1000,360,520,false);}
            else if(case1Dialogue==1){drawChatMessage(c,amirFace,"یک اختلاف مالی پیش اومده و می‌خوام بدونم از نظر حقوقی چه مدارکی به دردم می‌خوره.",620,420,390,980,true);drawChatMessage(c,lawyerFace,"چه مدرکی داری؟",90,1040,360,520,false);}
            else{drawChatMessage(c,amirFace,"هرچی مدرک دارم داخل این کیفه.",620,420,390,900,true);drawChatMessage(c,lawyerFace,"کیف رو بررسی می‌کنم.",90,1040,360,520,false);}
        }
        void drawChatMessage(Canvas c,Bitmap avatar,String s,float x,float y,float w,float maxW,boolean right){
            float h=right?250:190;round(c,x,y,x+w,y+h,30,right?0xe82a3942:0xe81e4b62);if(avatar!=null){float cx=right?x+w-62:x+62,cy=y+58,r=48;c.save();Path path=new Path();path.addCircle(cx,cy,r,Path.Direction.CW);c.clipPath(path);c.drawBitmap(avatar,null,new RectF(cx-r,cy-r,cx+r,cy+r),p);c.restore();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(0xffe3c07c);c.drawCircle(cx,cy,r,p);p.setStyle(Paint.Style.FILL);}float l=right?x+22:x+92,r=right?x+w-92:x+w-22;textBox(c,s,l,y+18,r,y+h-18,28,Color.WHITE,false);
        }

                void drawThoughtBubble(Canvas c,String s,float cx,float cy,float w,float h){
            p.setColor(0xeefffdf8); c.drawOval(new RectF(cx-w/2,cy-h/2,cx+w/2,cy+h/2),p);
            p.setColor(0xeefffdf8); c.drawCircle(cx-w*0.30f,cy+h*0.43f,24,p); c.drawCircle(cx-w*0.39f,cy+h*0.56f,13,p);
            text(c,s,cx,cy-48,27,0xff222222,true,Layout.Alignment.ALIGN_CENTER,w-45);
        }

        void drawCase1Evidence(Canvas c){
            p.setColor(0xff0b0f12);c.drawRect(0,0,1080,2400,p);drawPannedImage(c,evidenceOpen,evidencePan,120,1740);text(c,"محتویات کیف",965,1810,30,0xffe5c58f,true,Layout.Alignment.ALIGN_OPPOSITE,820);text(c,(phoneSolved?"✓ گوشی":"○ گوشی")+"   "+(receiptSolved?"✓ رسید":"○ رسید")+"   "+(dvdSolved?"✓ DVD":"○ DVD"),540,1880,28,0xffeadfce,true,Layout.Alignment.ALIGN_CENTER,850);drawEvidenceBackButton(c);drawEvidenceNotice(c);
        }
        void drawEvidenceBackButton(Canvas c){round(c,270,2180,810,2315,30,0xe8173c52);text(c,"بازگشت به محتویات کیف",540,2265,30,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,500);}
        void drawEvidenceNotice(Canvas c){if(evidenceNoticeUntil<=SystemClock.uptimeMillis())return;float rem=evidenceNoticeUntil-SystemClock.uptimeMillis();float a=Math.min(1f,rem/500f);int al=(int)(235*a);round(c,285,1180,795,1370,34,(al<<24)|0xff1c5b4b);text(c,"مدرک پیدا شد ✓",540,1240,34,(al<<24)|Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,500);text(c,"ذخیره شد و می‌توانی به کیف برگردی",540,1305,23,(al<<24)|0xffe7f3ed,true,Layout.Alignment.ALIGN_CENTER,500);postInvalidateOnAnimation();}

                void drawCase1Phone(Canvas c){
            p.setColor(0xff080b10); c.drawRect(0,0,1080,2400,p);
            round(c,125,155,955,2265,92,0xff050607);
            round(c,145,175,935,2245,76,0xff1b1e22);
            round(c,168,198,912,2222,58,0xff0b1118);
            round(c,505,214,575,232,12,0xff030405);
            p.setColor(0xff23384a); c.drawCircle(620,223,5,p);
            if(!phoneSolved){
                drawPhoneStatus(c,"رمز عبور",true);
                textBox(c,"گوشی قفل است",215,390,865,500,40,Color.WHITE,true);
                textBox(c,phoneInput.length()==0?"•  •  •  •":phoneDots(),270,635,810,730,42,Color.WHITE,true);
                String[][] keys={{"۱","۲","۳"},{"۴","۵","۶"},{"۷","۸","۹"},{"⌫","۰","✓"}};
                for(int r=0;r<4;r++)for(int col=0;col<3;col++){
                    float x=325+col*180,y=830+r*160;
                    round(c,x-62,y-62,x+62,y+62,31,(r==3&&col==2)?0xff2c7658:0xff1e2b35);
                    textBox(c,keys[r][col],x-62,y-62,x+62,y+62,35,Color.WHITE,true);
                }
            }else{
                drawPhoneStatus(c,phonePage==0?"۱۴:۳۲":phonePage==1?"پیام‌ها":"بانک ملت",false);
                if(phonePage==0) drawPhoneHome(c);
                else if(phonePage==1) drawPhoneMessages(c);
                else drawPhoneTransfer(c);
            }
            drawEvidenceBackButton(c);drawEvidenceNotice(c);
        }
        void drawPhoneStatus(Canvas c,String title,boolean lock){
            text(c,title,780,255,22,Color.WHITE,true,Layout.Alignment.ALIGN_OPPOSITE,250);
            text(c,lock?"۱۴:۳۲":"۴G   ۸۷٪",270,255,22,0xffd8e1e7,true,Layout.Alignment.ALIGN_NORMAL,250);
            p.setColor(0x332f8ab8); c.drawRect(170,290,910,294,p);
        }
        void drawPhoneHome(Canvas c){
            p.setShader(new LinearGradient(170,310,910,2050,0xff152b3b,0xff081019,Shader.TileMode.CLAMP));
            c.drawRoundRect(170,310,910,2050,45,45,p); p.setShader(null);
            textBox(c,"صفحه اصلی",220,370,860,450,27,Color.WHITE,true);
            drawAppIcon(c,300,620,"پیام‌ها",0xff2f78a8,"✉");
            drawAppIcon(c,540,620,"بانک ملت",0xff315f4b,"▣");
            drawAppIcon(c,780,620,"تماس‌ها",0xff3d6b49,"☎");
            drawAppIcon(c,300,930,"یادداشت",0xff79613a,"✎");
            drawAppIcon(c,540,930,"گالری",0xff5d496e,"▣");
            drawAppIcon(c,780,930,"تنظیمات",0xff46515b,"⚙");
            textBox(c,"برای بررسی پرونده، پیام‌ها و رسید بانکی را باز کن.",225,1740,855,1850,23,0xffd5e0e7,false);
        }
        void drawAppIcon(Canvas c,float x,float y,String label,int color,String glyph){
            round(c,x-70,y-70,x+70,y+70,32,color);
            textBox(c,glyph,x-55,y-55,x+55,y+55,42,Color.WHITE,true);
            textBox(c,label,x-100,y+85,x+100,y+135,20,Color.WHITE,true);
        }
        void drawPhoneMessages(Canvas c){
            textBox(c,"گفت‌وگوی پرونده",220,355,860,435,27,Color.WHITE,true);
            round(c,235,490,845,760,32,0xff20333d);
            textBox(c,"امیر",270,515,810,565,22,0xffe2bb78,true);
            textBox(c,"من بابت خرید دوچرخه پول رو منتقل کردم. رسیدش هم توی کیفمه.",270,570,810,735,24,Color.WHITE,false);
            round(c,235,820,845,1120,32,0xff173a52);
            textBox(c,"مخاطب پرونده",270,845,810,895,21,0xff9ec8e2,true);
            textBox(c,"رسید انتقال رو نگه دار؛ شماره پیگیری برای تطبیق لازمه.",270,900,810,1090,24,Color.WHITE,false);
            round(c,235,1180,845,1480,32,0xff20333d);
            textBox(c,"پیام بانکی",270,1205,810,1255,21,0xffe2bb78,true);
            textBox(c,"انتقال وجه به کارت مقصد\nمبلغ: ۱٬۰۰۰٬۰۰۰٬۰۰۰ ریال\nساعت: ۱۵:۳۲:۴۷",270,1260,810,1445,24,Color.WHITE,false);
        }
        void drawPhoneTransfer(Canvas c){
            textBox(c,"رسید انتقال وجه",220,355,860,435,27,Color.WHITE,true);
            round(c,235,490,845,1660,35,0xfff0eee8);
            textBox(c,"بانک ملت",300,535,780,610,28,0xff222a30,true);
            textBox(c,"رسید تراکنش حساب",300,640,780,705,26,0xff222a30,true);
            textBox(c,"تاریخ و زمان: ۱۴۰۴/۰۶/۲۵ ۱۵:۳۲:۴۷",280,760,800,850,21,0xff222a30,false);
            textBox(c,"نوع تراکنش: انتقال وجه کارت به کارت",280,865,800,955,21,0xff222a30,false);
            textBox(c,"مبلغ: ۱٬۰۰۰٬۰۰۰٬۰۰۰ ریال",280,975,800,1065,24,0xff222a30,true);
            textBox(c,"شماره پیگیری: ۳۷۸۶۵۴۲۲۱",280,1085,800,1175,21,0xff222a30,false);
            textBox(c,"شماره کارت مقصد: ۶۲۱۹-۸۶۱۰-۷۴۳۲-۱۰۸۹",280,1195,800,1285,20,0xff222a30,false);
            textBox(c,"صاحب کارت: علی رضایی",280,1305,800,1395,21,0xff222a30,false);
            textBox(c,"این اطلاعات را با رسید پازل تطبیق بده.",280,1510,800,1600,21,0xff7a5c2c,true);
        }

        String phoneDots(){
            StringBuilder b=new StringBuilder();
            for(int i=0;i<4;i++) b.append(i<phoneInput.length()?"●":"○").append(" ");
            return b.toString();
        }

        void drawCase1Dvd(Canvas c){
            p.setColor(0xff0b0f12); c.drawRect(0,0,1080,2400,p);
            round(c,55,150,1025,1745,34,0xff121a20);

            Bitmap frame=dvdFrames[Math.max(0,Math.min(19,dvdFrameIndex))];
            if(frame!=null){
                float fw=860f, fh=fw*frame.getHeight()/frame.getWidth();
                float left=(1080f-fw)/2f, top=300f;
                c.drawBitmap(frame,null,new RectF(left,top,left+fw,top+fh),p);
                round(c,left,top,left+fw,top+fh,8,0x00000000);
                text(c,"CAM 01   •   فریم "+toFa(dvdFrameIndex+1)+" از ۲۰",left+fw-20,top+48,25,Color.WHITE,true,Layout.Alignment.ALIGN_OPPOSITE,500);
            }

            text(c,"فیلم دوربین مداربسته",540,185,38,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,800);
            text(c,"فریم‌ها را بررسی کن و لحظه تحویل مدرک را پیدا کن.",540,905,29,0xffddcbb0,true,Layout.Alignment.ALIGN_CENTER,850);

            // Timeline: 20 selectable CCTV frames; the correct answer is intentionally not revealed.
            // Frame 17 is the exact document-handover moment.
            round(c,140,1050,940,1130,20,0xff263640);
            for(int i=0;i<20;i++){
                float px=155f+i*(770f/19f);
                p.setColor(i==dvdFrameIndex?0xffe0bb72:0xff6e7e86);
                c.drawCircle(px,1090, i==dvdFrameIndex?16:8,p);
            }
            text(c,"۰۱",155,1190,24,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,90);
            text(c,"۱۰",540,1190,24,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,90);
            text(c,"۲۰",925,1190,24,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,90);

            round(c,120,1280,360,1435,26,0xff263944);
            text(c,"فریم قبلی",240,1375,29,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,210);
            round(c,720,1280,960,1435,26,0xff263944);
            text(c,"فریم بعدی",840,1375,29,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,210);

            if(dvdSolved){
                round(c,210,1510,870,1660,28,0xff1f4b38);
                text(c,"لحظه درست پیدا شد ✓",540,1608,31,0xffe5f4e9,true,Layout.Alignment.ALIGN_CENTER,600);
            }else{
                round(c,210,1510,870,1660,28,0xff17435d);
                text(c,"انتخاب این فریم",540,1608,31,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,600);
            }

            drawEvidenceBackButton(c);drawEvidenceNotice(c);
        }

        void drawCase1Receipts(Canvas c){
            p.setColor(0xff0b0f12); c.drawRect(0,0,1080,2400,p);
            round(c,35,155,1045,1110,30,0xff18242b);
            textBox(c,"کادر قرارگیری رسید",60,175,1020,255,30,0xffe5c58f,true);
            for(int i=0;i<22;i++) if(receiptOnBoard[i] && receiptPieces[i]!=null) drawReceiptPiece(c,i,receiptPieceX[i],receiptPieceY[i],0,receiptRot[i]);
            round(c,35,1140,1045,1865,30,0xff131c21);
            textBox(c,"تکه‌های به‌هم‌ریخته",60,1165,1020,1245,30,0xffe5c58f,true);
            for(int slot=0;slot<22;slot++) if(!receiptOnBoard[trayOrder[slot]]){
                int i=trayOrder[slot]; int col=slot%5,row=slot/5; float cx=150+col*190,cy=1325+row*125;
                drawReceiptPiece(c,i,cx,cy,105,0);
                if(i==selectedReceiptPiece){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(0xffe6bd70);c.drawRoundRect(cx-62,cy-55,cx+62,cy+55,16,16,p);p.setStyle(Paint.Style.FILL);}
            }
            textBox(c,"تکه انتخاب‌شده: "+(selectedReceiptPiece<0?"هیچ‌کدام":toFa(selectedReceiptPiece+1)),250,1885,830,1960,25,0xffe4d8c9,true);
            drawArrowButton(c,540,1960,"▲"); drawArrowButton(c,540,2125,"▼");
            drawArrowButton(c,360,2042,"◀"); drawArrowButton(c,720,2042,"▶");
            round(c,900,1920,1015,2035,28,0xff6b4f2e); textBox(c,"↻",900,1920,1015,2035,38,Color.WHITE,true);
            drawEvidenceBackButton(c);drawEvidenceNotice(c);
        }

        void drawReceiptPiece(Canvas c,int i,float cx,float cy,float maxSize,float rot){
            Bitmap b=receiptPieces[i]; if(b==null)return;
            float ratio=maxSize>0 ? 0.27f : 0.63f;
            float w=b.getWidth()*ratio,h=b.getHeight()*ratio;
            c.save(); c.rotate(rot,cx,cy); c.drawBitmap(b,null,new RectF(cx-w/2,cy-h/2,cx+w/2,cy+h/2),p); c.restore();
        }

        void initReceiptPuzzle(){
            final int[] sx={70,300,535,790,1035,65,320,550,925,60,335,660,875,1190,55,600,820,1195,50,400,800,1190};
            final int[] sy={10,5,15,10,10,220,215,215,220,435,430,425,430,430,710,700,705,700,895,895,895,890};
            for(int i=0;i<22;i++){
                receiptOnBoard[i]=false; receiptLocked[i]=false; receiptRot[i]=0;
                receiptTargetX[i]=55 + sx[i]*0.63f + receiptPieces[i].getWidth()*0.63f/2f;
                receiptTargetY[i]=210 + sy[i]*0.63f + receiptPieces[i].getHeight()*0.63f/2f;
                trayOrder[i]=i;
            }
            java.util.Random rnd=new java.util.Random(1404);
            for(int i=21;i>0;i--){int j=rnd.nextInt(i+1);int t=trayOrder[i];trayOrder[i]=trayOrder[j];trayOrder[j]=t;}
            selectedReceiptPiece=-1; receiptSolved=false;
        }

        void drawArrowButton(Canvas c,float x,float y,String s){round(c,x-65,y-55,x+65,y+55,25,0xff263b46);text(c,s,x,y+16,34,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,110);}


        void drawCase(Canvas c) {
            int step=Math.min(storyStep,3);
            Bitmap person=caseCharacter(selectedCase);
            float targetH=step==0?760:690;
            drawCharacter(c,person,790,1450,targetH);
            panel(c,55,1540,1025,2240,0xe90c171e);
            text(c,"پرونده "+toFa(selectedCase)+"  |  "+casePeople[selectedCase],965,1630,28,0xffe0b96e,true,Layout.Alignment.ALIGN_OPPOSITE,900);
            text(c,caseIssues[selectedCase],965,1715,30,0xffd7cbb9,true,Layout.Alignment.ALIGN_OPPOSITE,900);
            text(c,story[selectedCase][step],965,1845,34,Color.WHITE,true,Layout.Alignment.ALIGN_OPPOSITE,900);
            round(c,100,2090,980,2215,30,0xff17435d);
            text(c,step<3?"ادامه روایت":"ورود به بخش آموزشی",540,2170,34,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,850);
        }

        Bitmap caseCharacter(int n){
            if(n==2 || n==5) return officer;
            if(n==4) return judge;
            if(n==3) return lawyer;
            return client;
        }

        void drawLesson(Canvas c) {
            panel(c,55,230,1025,2220,0xe9101920);
            text(c,"مفهوم حقوقی پرونده",970,390,40,Color.WHITE,true,Layout.Alignment.ALIGN_OPPOSITE,880);
            text(c,lesson[selectedCase][0],970,510,34,0xfff2eadc,true,Layout.Alignment.ALIGN_OPPOSITE,900);
            round(c,90,1330,990,1670,28,0xff1b3340);
            text(c,lesson[selectedCase][1],930,1435,29,0xffead9bc,true,Layout.Alignment.ALIGN_OPPOSITE,820);
            text(c,"حالا سؤال پرونده را حل کن.",540,1815,32,0xffe0c38f,true,Layout.Alignment.ALIGN_CENTER,800);
            round(c,120,1900,960,2070,30,0xff17435d);
            text(c,"ورود به تصمیم حقوقی",540,2005,36,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,800);
        }

        void drawQuiz(Canvas c) {
            panel(c,45,220,1035,2225,0xe9101920);
            text(c,questions[selectedCase],960,390,36,Color.WHITE,true,Layout.Alignment.ALIGN_OPPOSITE,900);
            for(int i=0;i<3;i++){
                float top=650+i*360;
                int color=answerChosen ? (i==correctIndex[selectedCase]?0xff235c43:0xff422a2c) : 0xff173c51;
                round(c,95,top,985,top+245,28,color);
                text(c,options[selectedCase][i],930,top+105,31,Color.WHITE,true,Layout.Alignment.ALIGN_OPPOSITE,800);
                if(answerChosen && i==correctIndex[selectedCase]) text(c,"✓",130,top+155,38,0xffb7e7c8,true,Layout.Alignment.ALIGN_CENTER,70);
            }
            if(answerChosen){
                text(c,correct?"پاسخ درست است":"پاسخ انتخابی درست نیست",540,1785,35,correct?0xff9ee0ba:0xffffb5ad,true,Layout.Alignment.ALIGN_CENTER,800);
                text(c,explanations[selectedCase],930,1880,29,0xffe7dac8,true,Layout.Alignment.ALIGN_OPPOSITE,820);
                round(c,170,2070,910,2185,28,0xff294552);
                text(c,"مشاهده نتیجه",540,2145,31,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,700);
            }
        }

        void drawResult(Canvas c) {
            panel(c,75,420,1005,1980,0xe90f1920);
            text(c,correct?"پرونده با موفقیت پیش رفت":"نیاز به مرور و تلاش دوباره",540,720,48,correct?0xffd8eddb:0xffffc1ba,true,Layout.Alignment.ALIGN_CENTER,850);
            text(c,correct?"مفهوم این پرونده به بخش پیشرفت اضافه شد.":"پاسخ را مرور کن و دوباره تصمیم بگیر.",540,835,31,0xffe5d9c8,true,Layout.Alignment.ALIGN_CENTER,850);
            round(c,150,1080,930,1250,30,0xff17435d);
            text(c,correct?(selectedCase<5?"بازگشت به کتابخانه":"پایان پنج پرونده") : "تلاش دوباره",540,1185,34,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,760);
        }

        void drawProgress(Canvas c) {
            panel(c,75,360,1005,2070,0xe90f1920);
            int completed=prefs.getInt("completed",0);
            text(c,"پیشرفت آموزشی",540,510,46,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,850);
            text(c,"پرونده‌های تکمیل‌شده: "+toFa(completed)+" از ۵",540,700,38,0xffeadfcf,true,Layout.Alignment.ALIGN_CENTER,850);
            for(int i=1;i<=5;i++){
                float y=850+(i-1)*210;
                round(c,150,y,930,y+150,24,i<=completed?0xff23543f:0xff263139);
                text(c,"پرونده "+toFa(i),850,y+62,30,Color.WHITE,true,Layout.Alignment.ALIGN_OPPOSITE,650);
                text(c,i<=completed?"تکمیل شده":"در انتظار یادگیری",850,y+105,23,0xffd2c6b6,true,Layout.Alignment.ALIGN_OPPOSITE,650);
            }
        }

        void drawArchive(Canvas c) {
            panel(c,75,360,1005,2070,0xe90f1920);
            text(c,"بایگانی پرونده‌ها",540,510,44,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,850);
            int completed=prefs.getInt("completed",0);
            if(completed==0){text(c,"هنوز پرونده‌ای تکمیل نشده است.",540,800,32,0xffd8cdbd,true,Layout.Alignment.ALIGN_CENTER,850);return;}
            for(int i=1;i<=completed;i++){
                float y=700+(i-1)*245;
                text(c,"پرونده "+toFa(i),900,y,32,0xffe0bd7b,true,Layout.Alignment.ALIGN_OPPOSITE,700);
                text(c,titles[i],900,y+55,27,0xffeee5d8,true,Layout.Alignment.ALIGN_OPPOSITE,700);
            }
        }

        void drawLawBook(Canvas c) {
            panel(c,65,300,1015,2180,0xe90e1820);
            text(c,"کتاب قانون",540,440,48,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,850);
            text(c,"راهنمای سریع مفاهیم این پنج پرونده",540,540,29,0xffd5bd98,true,Layout.Alignment.ALIGN_CENTER,850);
            String[] terms={"دادخواست","خواهان و خوانده","اجزای دادخواست","خواسته","دلیل"};
            String[] defs={"وسیله طرح دعوا از مسیر قانونی.","نقش طرفین دعوا را مشخص می‌کند.","اطلاعات ضروری برای شناسایی و بیان دعوا.","چیزی که خواهان از دادگاه درخواست می‌کند.","وسیله اثبات یا تقویت ادعا در حدود قانون."};
            for(int i=0;i<5;i++){
                float y=700+i*270;
                text(c,terms[i],930,y,32,0xffe1bb74,true,Layout.Alignment.ALIGN_OPPOSITE,760);
                text(c,defs[i],930,y+70,27,0xffeee6d9,true,Layout.Alignment.ALIGN_OPPOSITE,760);
            }
        }

        void drawSettings(Canvas c) {
            p.setColor(0x88000000); c.drawRect(0,0,1080,2400,p);
            panel(c,130,600,950,1800,0xf017252d);
            text(c,"تنظیمات",540,750,48,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,700);
            round(c,190,900,890,1080,30,0xff173c51);
            text(c,prefs.getBoolean("music",true)?"موسیقی: روشن":"موسیقی: خاموش",540,1010,32,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,650);
            round(c,190,1160,890,1340,30,0xff294552);
            text(c,"افکت صدا: روشن",540,1270,32,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,650);
            round(c,190,1420,890,1600,30,0xff3b2729);
            text(c,"خروج از بازی",540,1530,32,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,650);
        }

        void drawExit(Canvas c) {
            p.setColor(0xaa000000); c.drawRect(0,0,1080,2400,p);
            panel(c,140,900,940,1510,0xff17242c);
            text(c,"از بازی خارج می‌شوی؟",540,1040,42,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,750);
            round(c,200,1160,520,1310,28,0xff294552); text(c,"خیر",360,1255,32,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,260);
            round(c,560,1160,880,1310,28,0xff4a282b); text(c,"بله",720,1255,32,Color.WHITE,true,Layout.Alignment.ALIGN_CENTER,260);
        }

        void drawCharacter(Canvas c, Bitmap b, float cx, float bottom, float targetH) {
            if(b==null)return;
            float ratio=targetH/b.getHeight();
            float w=b.getWidth()*ratio;
            RectF d=new RectF(cx-w/2,bottom-targetH,cx+w/2,bottom);
            p.setAlpha(255); c.drawBitmap(b,null,d,p);
        }

        void round(Canvas c,float l,float t,float r,float b,float rad,int color){p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawRoundRect(l,t,r,b,rad,rad,p);}
        void panel(Canvas c,float l,float t,float r,float b,int color){round(c,l,t,r,b,34,color);}

        void textBox(Canvas c,String s,float l,float t,float r,float b,float size,int color,boolean bold){
            float w=r-l, h=b-t;
            tp.setTextSize(size); tp.setColor(color); tp.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));
            StaticLayout sl=StaticLayout.Builder.obtain(s,0,s.length(),tp,(int)w)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER).setTextDirection(TextDirectionHeuristics.RTL)
                    .setIncludePad(true).build();
            float ty=t+(h-sl.getHeight())/2f;
            c.save(); c.translate(l,Math.max(t,ty)); sl.draw(c); c.restore();
        }

        void text(Canvas c,String s,float x,float y,float size,int color,boolean bold,Layout.Alignment align,float width){
            tp.setTextSize(size); tp.setColor(color); tp.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));
            StaticLayout sl=StaticLayout.Builder.obtain(s,0,s.length(),tp,(int)width)
                    .setAlignment(align).setTextDirection(TextDirectionHeuristics.RTL).setIncludePad(true).build();
            float tx = align==Layout.Alignment.ALIGN_CENTER ? x-width/2f : x-width;
            c.save(); c.translate(tx,y); sl.draw(c); c.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            float x=(e.getX()-ox)/scale, y=(e.getY()-oy)/scale;
            if(screen==CASE_ROOM){
                return handleCaseRoomTouch(e,x,y);
            }
            if(screen==CASE1_AMIR || screen==CASE1_EVIDENCE){
                return handleCase1SceneTouch(e,x,y);
            }
            if(screen==CASE1_RECEIPTS){
                if(e.getAction()==MotionEvent.ACTION_UP && in(x,y,270,2180,810,2315)){goTo(CASE1_EVIDENCE);return true;}
                return handleReceiptTouch(e,x,y);
            }
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;

            if(exitConfirm){
                if(in(x,y,560,1160,880,1310)){finishActivity();return true;}
                if(in(x,y,200,1160,520,1310)){exitConfirm=false;invalidate();return true;}
                return true;
            }
            if(settings){
                if(in(x,y,190,900,890,1080)){toggleMusic();invalidate();return true;}
                if(in(x,y,190,1160,890,1340)){Toast.makeText(getContext(),"افکت صدا در نسخه پایه فعال است.",Toast.LENGTH_SHORT).show();return true;}
                if(in(x,y,190,1420,890,1600)){askExit();return true;}
                if(in(x,y,60,0,1020,560)){settings=false;invalidate();return true;}
                return true;
            }

            if(screen==START){if(in(x,y,300,1030,780,1260)){goTo(OFFICE);}return true;}

            // Large, separated top controls: back on left, settings on right.
            if(screen!=OFFICE && in(x,y,35,82,195,220)){
                if(screen==CASE1_AMIR){history.clear();history.push(LIBRARY);screen=CASE_ROOM;stopMusic();invalidate();return true;}
                goBack();return true;
            }
            if(in(x,y,885,82,1048,220)){settings=true;invalidate();return true;}

            if(screen==OFFICE){
                if(in(x,y,0,280,520,1370)){goTo(LIBRARY);return true;}
                if(in(x,y,650,820,930,1560)){goTo(ARCHIVE);return true;}
                if(in(x,y,850,1450,1080,2200)){goTo(PROGRESS);return true;}
                if(in(x,y,0,1760,310,2220)){goTo(LAWBOOK);return true;}
                if(in(x,y,300,1660,820,2300)){goTo(LIBRARY);return true;}
            }
            else if(screen==LIBRARY){
                for(int i=1;i<=5;i++){
                    float cx=folderX(i), cy=folderY(i);
                    if(in(x,y,cx-125,cy-125,cx+125,cy+125)){
                        if(i<=unlocked()){
                            selectedCase=i;
                            if(i==1){resetCase1New();goTo(CASE_ROOM);} else {storyStep=0;goTo(CASE);}
                        } else Toast.makeText(getContext(),"این پرونده هنوز قفل است.",Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
            }
            else if(screen==CASE_ROOM){
                int action=e.getAction();if(action==MotionEvent.ACTION_DOWN){roomTouchStartX=x;roomTouchStartY=y;roomPanStart=roomPan;roomDragging=false;return true;}if(action==MotionEvent.ACTION_MOVE){float dx=x-roomTouchStartX;if(Math.abs(dx)>10)roomDragging=true;roomPan=Math.max(0,Math.min(roomMaxPan(),roomPanStart-dx));invalidate();return true;}if(action==MotionEvent.ACTION_UP){float dx=x-roomTouchStartX;if(roomDragging||Math.abs(dx)>25){roomDragging=false;return true;}float z=roomZoom,baseH=2280f,baseW=baseH*caseRoomAmir.getWidth()/caseRoomAmir.getHeight();float iy=(y-120f-(baseH*(z-1f)/2f))/(baseH*z)*caseRoomAmir.getHeight();float ix=(x+roomPan)/(baseW*z)*caseRoomAmir.getWidth();if(ix>=240&&ix<=780&&iy>=190&&iy<=710){case1Dialogue=0;goTo(CASE1_AMIR);return true;}return true;}return true;
            }
                        else if(screen==CASE1_EVIDENCE){
                // exact open-bag image: phone / receipts / DVD hitboxes mapped to the displayed 16:9 image.
                if(in(x,y,180,350,445,590)){goTo(CASE1_PHONE);return true;}
                if(in(x,y,395,415,625,690)){goTo(CASE1_RECEIPTS);return true;}
                if(in(x,y,610,415,840,635)){goTo(CASE1_DVD);return true;}
            }
            else if(screen==CASE1_PHONE){
                if(in(x,y,270,2180,810,2315)){goTo(CASE1_EVIDENCE);return true;}
                if(!phoneSolved){
                    String[][] keys={{"۱","۲","۳"},{"۴","۵","۶"},{"۷","۸","۹"},{"⌫","۰","✓"}};
                    for(int r=0;r<4;r++)for(int col=0;col<3;col++) if(in(x,y,263+col*180,768+r*160,387+col*180,892+r*160)){
                        String k=keys[r][col];
                        if(k.equals("⌫")){if(phoneInput.length()>0)phoneInput=phoneInput.substring(0,phoneInput.length()-1);}
                        else if(k.equals("✓")){if(phoneInput.equals("۱۳۹۰")){phoneSolved=true;phonePage=0;showPuzzleSolvedNotice();checkCase1Puzzles();}}
                        else if(phoneInput.length()<4)phoneInput+=k;
                        invalidate(); return true;
                    }
                } else {
                    if(in(x,y,225,545,375,695)){phonePage=1;invalidate();return true;}
                    if(in(x,y,465,545,615,695)){phonePage=2;invalidate();return true;}
                    if(in(x,y,330,2110,750,2190)){phonePage=0;invalidate();return true;}
                }
            }
            else if(screen==CASE1_DVD){
                if(in(x,y,270,2180,810,2315)){goTo(CASE1_EVIDENCE);return true;}
                if(in(x,y,250,1900,830,2070)){goTo(CASE1_EVIDENCE);return true;}
                if(dvdSolved){return true;}
                if(in(x,y,120,1280,360,1435)){dvdFrameIndex=Math.max(0,dvdFrameIndex-1);invalidate();return true;}
                if(in(x,y,720,1280,960,1435)){dvdFrameIndex=Math.min(19,dvdFrameIndex+1);invalidate();return true;}
                if(in(x,y,120,1020,960,1160)){
                    float t=(x-155f)/770f;
                    dvdFrameIndex=Math.max(0,Math.min(19,Math.round(t*19f)));
                    invalidate();return true;
                }
                if(in(x,y,210,1510,870,1660)){
                    if(dvdFrameIndex==16){
                        dvdSolved=true;
                        showPuzzleSolvedNotice();
                        checkCase1Puzzles();
                    }else{
                        Toast.makeText(getContext(),"این فریم لحظه تحویل مدرک نیست.",Toast.LENGTH_SHORT).show();
                    }
                    invalidate();return true;
                }
            }
            else if(screen==CASE1_RECEIPTS){
                if(in(x,y,270,2180,810,2315)){goTo(CASE1_EVIDENCE);return true;}
                handleReceiptTouch(e,x,y); return true;
            }
            else if(screen==CASE){
                if(in(x,y,80,2040,1000,2250)){
                    if(storyStep<3){storyStep++;invalidate();} else goTo(LESSON);
                    return true;
                }
            }
            else if(screen==LESSON){if(in(x,y,100,1850,980,2100)){answerChosen=false;goTo(QUIZ);return true;}}
            else if(screen==QUIZ){
                if(answerChosen && in(x,y,120,2020,960,2210)){goTo(RESULT);return true;}
                if(!answerChosen){
                    for(int i=0;i<3;i++) if(in(x,y,80,630+i*360,1000,920+i*360)){
                        answerChosen=true; correct=i==correctIndex[selectedCase]; invalidate(); return true;
                    }
                }
            }
            else if(screen==RESULT){
                if(in(x,y,120,1050,960,1280)){
                    if(correct){
                        int done=prefs.getInt("completed",0);
                        if(selectedCase>done) prefs.edit().putInt("completed",selectedCase).putInt("unlocked",Math.min(5,selectedCase+1)).apply();
                        history.clear();
                        history.push(OFFICE);
                        screen=LIBRARY;
                        stopMusic();
                        invalidate();
                    } else {
                        answerChosen=false;
                        if(!history.isEmpty() && history.peek()==QUIZ) history.pop();
                        screen=QUIZ;
                        invalidate();
                    }
                    return true;
                }
            }
            return true;
        }

        boolean handleReceiptTouch(MotionEvent e,float x,float y){
            int action=e.getAction();
            if(action==MotionEvent.ACTION_DOWN){receiptDragging=false;receiptDownX=x;receiptDownY=y;if(selectedReceiptPiece>=0&&!receiptLocked[selectedReceiptPiece]){float w=receiptPieces[selectedReceiptPiece].getWidth()*0.75f,h=receiptPieces[selectedReceiptPiece].getHeight()*0.75f;if(in(x,y,receiptPieceX[selectedReceiptPiece]-w/2,receiptPieceY[selectedReceiptPiece]-h/2,receiptPieceX[selectedReceiptPiece]+w/2,receiptPieceY[selectedReceiptPiece]+h/2)){receiptStartX=receiptPieceX[selectedReceiptPiece];receiptStartY=receiptPieceY[selectedReceiptPiece];receiptDragging=true;return;}}for(int slot=0;slot<22;slot++){int i=trayOrder[slot];if(receiptOnBoard[i])continue;int col=slot%5,row=slot/5;float cx=150+col*190,cy=1325+row*125;if(in(x,y,cx-75,cy-65,cx+75,cy+65)){selectedReceiptPiece=i;receiptOnBoard[i]=true;receiptPieceX[i]=540;receiptPieceY[i]=650;receiptRot[i]=0;receiptStartX=540;receiptStartY=650;receiptDragging=true;invalidate();return;}}return;}
            if(action==MotionEvent.ACTION_MOVE&&receiptDragging&&selectedReceiptPiece>=0&&!receiptLocked[selectedReceiptPiece]){receiptPieceX[selectedReceiptPiece]=receiptStartX+(x-receiptDownX);receiptPieceY[selectedReceiptPiece]=receiptStartY+(y-receiptDownY);invalidate();return;}
            if(action==MotionEvent.ACTION_UP){if(receiptDragging&&selectedReceiptPiece>=0&&!receiptLocked[selectedReceiptPiece]){receiptDragging=false;checkReceiptPiece(selectedReceiptPiece);invalidate();return;}if(selectedReceiptPiece>=0&&!receiptLocked[selectedReceiptPiece]){if(in(x,y,900,1920,1015,2035)){receiptRot[selectedReceiptPiece]=(receiptRot[selectedReceiptPiece]+90)%360;checkReceiptPiece(selectedReceiptPiece);invalidate();return;}float dx=0,dy=0;if(in(x,y,475,1905,605,2015))dy=-22;else if(in(x,y,475,2070,605,2180))dy=22;else if(in(x,y,295,1987,425,2097))dx=-22;else if(in(x,y,655,1987,785,2097))dx=22;if(dx!=0||dy!=0){receiptPieceX[selectedReceiptPiece]+=dx;receiptPieceY[selectedReceiptPiece]+=dy;checkReceiptPiece(selectedReceiptPiece);invalidate();return;}}for(int i=0;i<22;i++)if(receiptOnBoard[i]&&!receiptLocked[i]){float w=receiptPieces[i].getWidth()*0.75f,h=receiptPieces[i].getHeight()*0.75f;if(in(x,y,receiptPieceX[i]-w/2,receiptPieceY[i]-h/2,receiptPieceX[i]+w/2,receiptPieceY[i]+h/2)){selectedReceiptPiece=i;invalidate();return true;}}}
            return true;
        }

                boolean handleCase1SceneTouch(MotionEvent e,float x,float y){
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount()>=2){roomPinching=true;roomPinchStartDistance=distance(e);roomZoomStart=roomZoom;return true;}
            if(action==MotionEvent.ACTION_MOVE && roomPinching && e.getPointerCount()>=2){float d=distance(e);if(roomPinchStartDistance>0){roomZoom=Math.max(1f,Math.min(2.2f,roomZoomStart*d/roomPinchStartDistance));roomPan=Math.max(0,Math.min(roomMaxPan(),roomPan));invalidate();}return true;}
            if(action==MotionEvent.ACTION_POINTER_UP){if(e.getPointerCount()<=2)roomPinching=false;return true;}
            if(action==MotionEvent.ACTION_UP&&in(x,y,35,82,195,220)){goBack();return true;}
            if(action==MotionEvent.ACTION_UP&&in(x,y,885,82,1048,220)){settings=true;invalidate();return true;}
            if(screen==CASE1_AMIR){
                if(action==MotionEvent.ACTION_DOWN){roomTouchStartX=x;roomTouchStartY=y;roomPanStart=roomPan;roomDragging=false;return true;}
                if(action==MotionEvent.ACTION_MOVE){float dx=x-roomTouchStartX;if(Math.abs(dx)>10)roomDragging=true;roomPan=Math.max(0,Math.min(roomMaxPan(),roomPanStart-dx));invalidate();return true;}
                if(action==MotionEvent.ACTION_UP){float dx=x-roomTouchStartX;if(roomDragging||Math.abs(dx)>25){roomDragging=false;return true;}if(case1Dialogue<2){case1Dialogue++;invalidate();return true;}
                    float baseH=2280f,baseW=baseH*caseRoomAmir.getWidth()/caseRoomAmir.getHeight(),z=Math.max(1f,Math.min(2.2f,roomZoom));
                    float iy=(y-120f-(baseH*(z-1f)/2f))/(baseH*z)*caseRoomAmir.getHeight();float ix=(x+roomPan)/(baseW*z)*caseRoomAmir.getWidth();
                    // Bag location in the evidence-dialogue room image. There is intentionally no floating button.
                    if(ix>=760&&ix<=1170&&iy>=430&&iy<=900){goTo(CASE1_EVIDENCE);return true;}return true;
                }
            }else{
                if(action==MotionEvent.ACTION_DOWN){evidenceDownX=x;evidenceStartPan=evidencePan;evidenceDragging=false;return true;}
                if(action==MotionEvent.ACTION_MOVE){float dx=x-evidenceDownX;if(Math.abs(dx)>12)evidenceDragging=true;evidencePan=Math.max(0,Math.min(pannedMax(evidenceOpen,120,1740),evidenceStartPan-dx));invalidate();return true;}
                if(action==MotionEvent.ACTION_UP){float dx=x-evidenceDownX;if(evidenceDragging||Math.abs(dx)>25){evidenceDragging=false;return true;}if(in(x,y,270,2180,810,2315)){goTo(CASE1_EVIDENCE);return true;}float imageH=1620f,imageW=imageH*evidenceOpen.getWidth()/evidenceOpen.getHeight(),ix=(x+evidencePan)/imageW*evidenceOpen.getWidth(),iy=(y-120f)/imageH*evidenceOpen.getHeight();if(ix>=120&&ix<=570&&iy>=420&&iy<=835){goTo(CASE1_PHONE);return true;}if(ix>=540&&ix<=1035&&iy>=400&&iy<=820){goTo(CASE1_RECEIPTS);return true;}if(ix>=1000&&ix<=1490&&iy>=390&&iy<=800){goTo(CASE1_DVD);return true;}return true;}
            }
            return true;
        }

        float pannedMax(Bitmap b,float top,float bottom){
            if(b==null)return 0f;
            float imageH=bottom-top;
            float imageW=imageH*b.getWidth()/b.getHeight();
            return Math.max(0f,imageW-1080f);
        }

        boolean checkReceiptPiece(int i){
            if(i<0 || i>=22 || receiptPieces[i]==null || !receiptOnBoard[i] || receiptLocked[i]) return false;
            float dx=receiptPieceX[i]-receiptTargetX[i];
            float dy=receiptPieceY[i]-receiptTargetY[i];
            float tol=58f;
            boolean correct=Math.abs(dx)<=tol && Math.abs(dy)<=tol && (((int)receiptRot[i]%360+360)%360)==0;
            if(correct){
                receiptPieceX[i]=receiptTargetX[i];
                receiptPieceY[i]=receiptTargetY[i];
                receiptRot[i]=0;
                receiptLocked[i]=true;
                showPuzzleSolvedNotice();
                checkCase1Puzzles();
            }
            return correct;
        }

        void drawPannedImage(Canvas c,Bitmap b,float pan,float top,float bottom){
            if(b==null)return;
            float h=bottom-top, w=h*b.getWidth()/b.getHeight();
            float px=Math.max(0f,Math.min(pan,Math.max(0f,w-1080f)));
            c.drawBitmap(b,null,new RectF(-px,top,w-px,bottom),p);
        }

        void showPuzzleSolvedNotice(){evidenceNoticeUntil=SystemClock.uptimeMillis()+1600L;showEvidenceBack=true;postInvalidateOnAnimation();}

        void checkCase1Puzzles(){
            boolean all=phoneSolved && dvdSolved;
            if(all){
                all=true;
                for(int i=0;i<22;i++) if(!receiptLocked[i]){all=false;break;}
            }
            receiptSolved=allReceiptLocked();
            if(phoneSolved && receiptSolved && dvdSolved){
                // The three evidence puzzles are the gate. Only after all three are solved does education open.
                history.clear();
                history.push(CASE1_EVIDENCE);
                answerChosen=false; correct=false; selectedCase=1;
                screen=LESSON; startMusic(); invalidate();
            }
        }

        boolean allReceiptLocked(){for(int i=0;i<22;i++)if(!receiptLocked[i])return false;return true;}

        boolean handleCaseRoomTouch(MotionEvent e,float x,float y){
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_POINTER_DOWN && e.getPointerCount()>=2){
                roomPinching=true; roomPinchStartDistance=distance(e); roomZoomStart=roomZoom; return true;
            }
            if(action==MotionEvent.ACTION_MOVE && roomPinching && e.getPointerCount()>=2){
                float d=distance(e); if(roomPinchStartDistance>0){roomZoom=Math.max(1f,Math.min(2.2f,roomZoomStart*d/roomPinchStartDistance));roomPan=Math.max(0,Math.min(roomMaxPan(),roomPan));invalidate();}return true;
            }
            if(action==MotionEvent.ACTION_POINTER_UP){if(e.getPointerCount()<=2)roomPinching=false;return true;}
            if(action==MotionEvent.ACTION_DOWN){roomTouchStartX=x;roomTouchStartY=y;roomPanStart=roomPan;roomDragging=false;return true;}
            if(action==MotionEvent.ACTION_MOVE){float dx=x-roomTouchStartX;if(Math.abs(dx)>10)roomDragging=true;roomPan=Math.max(0,Math.min(roomMaxPan(),roomPanStart-dx));invalidate();return true;}
            if(action==MotionEvent.ACTION_UP){float dx=x-roomTouchStartX;if(roomDragging||Math.abs(dx)>25){roomDragging=false;return true;}if(in(x,y,35,82,195,220)){goBack();return true;}if(in(x,y,885,82,1048,220)){settings=true;invalidate();return true;}
                float baseH=2280f,baseW=baseH*caseRoomAmir.getWidth()/caseRoomAmir.getHeight(),z=Math.max(1f,Math.min(2.2f,roomZoom));float iy=(y-120f-(baseH*(z-1f)/2f))/(baseH*z)*caseRoomAmir.getHeight();float ix=(x+roomPan)/(baseW*z)*caseRoomAmir.getWidth();
                if(ix>=240&&ix<=780&&iy>=190&&iy<=710){case1Dialogue=0;goTo(CASE1_AMIR);return true;}return true;
            }
            return true;
        }

        float distance(MotionEvent e){if(e.getPointerCount()<2)return 0f;float dx=e.getX(1)-e.getX(0),dy=e.getY(1)-e.getY(0);return (float)Math.sqrt(dx*dx+dy*dy);}

        boolean in(float x,float y,float l,float t,float r,float b){return x>=l&&x<=r&&y>=t&&y<=b;}
        int unlocked(){return Math.max(1,Math.min(5,prefs.getInt("unlocked",1)));}
        String toFa(int n){return String.valueOf(n).replace("0","۰").replace("1","۱").replace("2","۲").replace("3","۳").replace("4","۴").replace("5","۵").replace("6","۶").replace("7","۷").replace("8","۸").replace("9","۹");}

        void resetCase1New(){
            case1Dialogue=0;
            phoneInput=""; phoneSolved=false; phonePage=0; receiptSolved=false; dvdSolved=false; dvdFrameIndex=0;
            initReceiptPuzzle();
            roomPan=700f; roomZoom=1f;
            roomDragging=false;
            roomDownX=0f;
            roomStartPan=700f;
            amirPan=0f; amirDownX=0f; amirStartPan=0f; amirDragging=false;
            evidencePan=0f; evidenceDownX=0f; evidenceStartPan=0f; evidenceDragging=false;
        }

        void goTo(int next){
            if(screen!=next) history.push(screen);
            screen=next;
            if(next==LESSON||next==QUIZ||next==CASE||next==CASE_ROOM||next==CASE1_AMIR||next==CASE1_EVIDENCE||next==CASE1_PHONE||next==CASE1_RECEIPTS||next==CASE1_DVD) startMusic(); else stopMusic();
            invalidate();
        }

        boolean goBack(){
            if(exitConfirm||settings){exitConfirm=false;settings=false;invalidate();return true;}
            if(history.isEmpty())return false;
            screen=history.pop(); stopMusic(); invalidate(); return true;
        }

        void askExit(){exitConfirm=true;settings=false;invalidate();}
        void finishActivity(){((Activity)getContext()).finish();}

        void toggleMusic(){
            boolean on=prefs.getBoolean("music",true);
            prefs.edit().putBoolean("music",!on).apply();
            if(on) stopMusic(); else startMusic();
        }

        void startMusic(){
            if(!prefs.getBoolean("music",true))return;
            stopMusic();
            try{
                music=MediaPlayer.create(getContext(),R.raw.stage_music);
                if(music!=null){music.setLooping(true);music.setVolume(.55f,.55f);music.start();}
            }catch(Exception ignored){music=null;}
        }

        void pauseMusicForBackground(){if(music!=null){try{music.pause();}catch(Exception ignored){}}}
        void resumeMusicAfterBackground(){if(!prefs.getBoolean("music",true))return;if(music!=null){try{if(!music.isPlaying())music.start();return;}catch(Exception ignored){}}if(screen!=START)startMusic();}

        void stopMusic(){
            if(music!=null){try{music.stop();}catch(Exception ignored){} try{music.release();}catch(Exception ignored){} music=null;}
        }
    }
}
