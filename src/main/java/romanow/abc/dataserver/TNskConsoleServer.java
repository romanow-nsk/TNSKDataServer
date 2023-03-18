package romanow.abc.dataserver;

import romanow.abc.core.*;
import romanow.abc.core.API.RestAPIBase;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import romanow.abc.core.constants.Values;
import romanow.abc.core.constants.ValuesBase;
import romanow.abc.core.export.Excel;
import romanow.abc.core.export.ExcelX;
import romanow.abc.core.export.I_Excel;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

public class TNskConsoleServer  {
    protected I_DBTarget dbTarget;
    protected Class apiFace;
    private int lineCount=0;
    private String gblEncoding="";
    private boolean utf8;
    private TNskDataServer dataServer = new TNskDataServer();
    private I_ServerState serverBack = new I_ServerState() {
        @Override
        public void onStateChanged(ServerState serverState) {
            dataServer.delayInGUI(1,new Runnable() {
                public void run() {
                    System.out.println(serverState.toString());
                }
            });
        }
    };
    //---------------------------------------------------------------------
    public TNskConsoleServer(){
        Values.init();
        dbTarget = new DBExample();
        apiFace = RestAPIBase.class;
    }
    public TNskConsoleServer(I_DBTarget target, Class apiFace0){
        apiFace = apiFace0;
        dbTarget = target;
    }
    private int port;
    public void setTarget(){
        Retrofit retrofit=null;
        RestAPIBase service=null;
        final OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .readTimeout(Values.HTTPTimeOut, TimeUnit.SECONDS)
                .connectTimeout(Values.HTTPTimeOut, TimeUnit.SECONDS)
                .build();
        retrofit = new Retrofit.Builder()
                .baseUrl("http://localhost:"+port)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        service = (RestAPIBase) retrofit.create(apiFace);
        dbTarget.createAll(service, Values.DebugTokenPass);
    }
    public void startServer(TNskCommandStringData data){
        //------------------------------------------------------------------
        port = data.getPort();
        gblEncoding = System.getProperty("file.encoding");
        utf8 = gblEncoding.equals("UTF-8");
        final LogStream log = new LogStream(utf8, dataServer.getConsoleLog(),new I_String() {
            @Override
            public void onEvent(String ss) {
                System.err.println(ss);
                dataServer.addToLog(ss);
            }
        });
        if (data.isInit()){
            dataServer.startServer(port, data.getDbase(), serverBack,(true));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {}
                    setTarget();
                    System.setOut(new PrintStream(log));
                    System.setErr(new PrintStream(log));
                }
            }).start();
        }
        else {
            System.setOut(new PrintStream(log));
            dataServer.startServer(port, data.getDbase(), serverBack,(false));
            }
    }
    public static void main(String args[]) {
        //String ss[]= {"port:4571","init:"};
        String ss[]= {"port:4571"};
        if (args.length!=0)
            ss = args;
        TNskCommandStringData data = new TNskCommandStringData();
        data.parse(ss);
        ErrorList errors = data.getErrors();
        if (!errors.valid()){
            System.out.println("Ошибки командной строки:\n"+errors.toString());
            return;
        }
        System.out.println("Порт="+data.getPort());
        if (data.hasConf())
            System.out.println("Конфигурация="+data.getConf());
        TNskConsoleServer server = new TNskConsoleServer();
        if(data.hasUser())
            ValuesBase.env().superUser().setLoginPhone(data.getUser());
        if (data.hasPass())
            ValuesBase.env().superUser().setPassword(data.getPass());
        if (!data.hasImport())
            server.startServer(data);
        else{
            server.startServer(new TNskCommandStringData(data.getPort(), data.getDbase()));
            try {
                TNskDataServer db = server.dataServer;
                String fname = data.getImportXLS();
                I_Excel xls = fname.endsWith("xlsx") ? new ExcelX() : new Excel();
                db.mongoDB.clearDB();
                fname = System.getProperty("user.dir")+"/"+fname;
                System.out.println("Импорт БД из: "+fname);
                String zz = xls.load(fname,db.mongoDB);
                System.out.println(zz);
                db.shutdown();
                try {
                    Thread.sleep(5*1000);
                } catch (InterruptedException e) {}
                server.startServer(data);
            } catch (UniException e) {
                System.out.println("Ошибка импорта БД: "+e.toString());
            }
        }
    }
    }
