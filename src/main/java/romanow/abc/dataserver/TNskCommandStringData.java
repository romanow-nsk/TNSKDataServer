package romanow.abc.dataserver;

import lombok.Getter;

public class TNskCommandStringData extends CommandStringData{
    //---------------------------------- командная строка
    //  port:4569      - порт
    //  user:xxxx      - имя суперпользователя
    //  pass:xxxx      - пароль  суперпользователя
    //  conf:xxxx      - имя разворачиваемой конфигурации
    //  init           - инициализация БД
    //  dbase:xxxx     - тип СУБД
    @Getter private String conf=null;
    public boolean hasConf(){ return conf!=null; }
    @Override
    public boolean isOther(String ss){
        /*
        if (ss.startsWith("iec61850:")){
            try {
                IEC61850EquipIdx = Integer.parseInt(ss.substring(9).trim());
            } catch (Exception ee){
                getErrors().addError("Недопустимое значение параметра: "+ss);
            }
            return true;
        }
        else
        */
        if (ss.startsWith("conf:")){
            conf = ss.substring(5).trim();
            return true;
            }
        else
            return false;
        }
    public TNskCommandStringData(){}
    public TNskCommandStringData(int port0, String dBase0){
        super(port0,dBase0);
    }
}
