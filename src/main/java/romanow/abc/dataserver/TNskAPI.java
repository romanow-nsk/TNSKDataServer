package romanow.abc.dataserver;

import lombok.Getter;
import romanow.abc.core.ErrorList;
import romanow.abc.core.UniException;
import romanow.abc.core.entity.server.TServerData;
import romanow.abc.core.entity.subjectarea.*;
import romanow.abc.core.mongo.RequestStatistic;
import romanow.abc.core.prepare.GorTransImport;
import spark.Request;
import spark.Response;

public class TNskAPI extends APIBase {
    private TNskDataServer db;
    @Getter private TServerData serverData = new TServerData();
    public TNskAPI(TNskDataServer db0) {
        super(db0);
        db = db0;
        spark.Spark.get("/api/tnsk/import", apiGorTransImport);
        /*
        spark.Spark.post("/api/rating/group/add", apiAddGroupRating);
        spark.Spark.post("/api/rating/group/remove", apiRemoveGroupRating);
        spark.Spark.get("/api/rating/group/get", apiGetGroupRatings);
        spark.Spark.get("/api/rating/taking/get", apiGetTakingRatings);
        spark.Spark.post("/api/state/change",apiStateChange);
        spark.Spark.post("/api/rating/takingforall", apiSetTakingForAll);
        spark.Spark.get("/api/report/group/artifact", apiCreateGroupReportArtifact);
        spark.Spark.get("/api/report/group/table", apiCreateGroupReportTable);
         */
        }
    public void saveDB(GorTransImport gorTrans,ErrorList log) {
        try {
            long oid=0;
            db.mongoDB.clearTable(TStop.class.getSimpleName());
            for(TStop stop : gorTrans.getStops()){
                oid = db.mongoDB.add(stop);
                stop.setOid(oid);
                }
            log.addInfo("Остановки сохранены: "+gorTrans.getStops().size());
            db.mongoDB.clearTable(TSegment.class.getSimpleName());
            int cnt1=0,cnt2=0, cnt3=0;
            for(TSegment segment : gorTrans.getSegments()){
                oid = db.mongoDB.add(segment);
                segment.setOid(oid);
                cnt3+=segment.getPoints().size();
                for(TSegPoint point : segment.getPoints()){
                    point.getTSegment().setOid(oid);
                    db.mongoDB.add(point);
                    }
                }
            log.addInfo("Сегменты сохранены: "+gorTrans.getSegments().size());
            db.mongoDB.clearTable(TRoute.class.getSimpleName());
            for(TRoute route : gorTrans.getRoutes()){
                oid = db.mongoDB.add(route);
                route.setOid(oid);
                }
            log.addInfo("Маршруты сохранены: "+gorTrans.getRoutes().size());
            for(TRoute route : gorTrans.getRoutes()){
                cnt1+=route.getSegments().size();
                for(TRouteSegment segment : route.getSegments()){
                    segment.getTRoute().setOid(route.getOid());
                    segment.getSegment().setOidByRef();
                    segment.getNear1().setOidByRef();
                    segment.getNear2().setOidByRef();
                    db.mongoDB.add(segment);
                    }
                cnt2+=route.getStops().size();
                for(TRouteStop stop : route.getStops()){
                    stop.getTRoute().setOid(route.getOid());
                    stop.getStop().setOidByRef();
                    stop.setDiff(stop.getStop().getRef().getDiff());    // Расстояние из этой остановки
                    db.mongoDB.add(stop);
                    }
                }
            log.addInfo("Обработано в маршрутах: сегментов "+cnt1+", точек "+cnt3+ ", остановок "+cnt2);
        } catch (UniException ee) {
                log.addError("Ошибка импорта в БД: "+ee.toString());
                }
        }
    RouteWrap apiGorTransImport = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            if (!db.users.isOnlyForSuperAdmin(req,res))
                return null;
            ErrorList errorList=new ErrorList();
            GorTransImport gorTrans = new GorTransImport();
            gorTrans.importData(errorList);
            if (!errorList.valid())
                return errorList;
            saveDB(gorTrans,errorList);
            return errorList;
        }};
    /*
    RouteWrap apiStateChange = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamBody dbReq = new ParamBody(req, res, DBRequest.class);
            if (!dbReq.isValid()) return null;
            StateEntity entity = null;
            DBRequest dbRequest = (DBRequest) dbReq.getValue();
            try {
                entity = (StateEntity) dbRequest.get(new Gson());
                } catch (Exception ee){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Ошибка создания объекта для автомата "+dbRequest.getClassName()+"\n"+ee.toString());
                    return null;
                    }
            String stateClass = dbRequest.getClassName();
            long oid = entity.getOid();
            TransitionsFactory factory = Values.stateFactoryMap.get(stateClass);
            if (factory==null){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Не найден автомат "+stateClass);
                return null;
                }
            Class clazz = Values.EntityFactory().getClassForSimpleName(stateClass);
            if (clazz==null){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Не найден класс для автомата  "+stateClass);
                return null;
                }
            StateEntity oldEntity = (StateEntity)clazz.newInstance();
            if (!db.mongoDB.getById(oldEntity,oid)){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Не найден объект "+stateClass+" id="+oid);
                return null;
                }
            Transition transition = factory.getByState(oldEntity.getState(),entity.getState());
            if (transition==null){
                if (transition==null){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Не найдена функция перехода  "+oldEntity.getState()+"->"+entity.getState());
                    return null;
                    }
                }
            try {
                Class cls = Class.forName("romanow.abc.dataserver.statemashine."+factory.name+transition.transName);
                I_ServerTransition transition1 = (I_ServerTransition) cls.newInstance();
                String rez = transition1.onTransition(db,entity);
                if (rez.length()!=0){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, rez);
                    return null;
                    }
                db.mongoDB.update(entity);
                }catch (Exception ee){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Ошибка создания класса для автомата "+factory.name+transition.transName+"n"+ee.toString());
                    return null;
                    }
            return new JEmpty();
            }
        };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiAddGroupRating = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamBody groupRating = new ParamBody(req, res, SAGroupRating.class);
            if (!groupRating.isValid()) return null;
            SAGroupRating rating = (SAGroupRating)groupRating.getValue();
            long groupId = rating.getGroup().getOid();
            long ruleId = rating.getSemRule().getOid();
            long discId = rating.getSADiscipline().getOid();
            SAGroup group = new SAGroup();
            if (!db.mongoDB.getById(group,groupId,2)){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа id=" + groupId + " не найдена");
                return null;
                }
            SASemesterRule rule = new SASemesterRule();
            if (!db.mongoDB.getById(rule,ruleId)){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Регламент id=" + ruleId + " не найден");
                return null;
                }
            SADiscipline discipline = new SADiscipline();
            if (!db.mongoDB.getById(discipline,discId,1)){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Дисциплина id=" + discId + " не найдена");
                return null;
                }
            for(SAGroupRating groupRating1 : discipline.getRatings()){
                if (groupRating1.getGroup().getOid()==groupId){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Повторное добавление рейтинга дисциплина-группа");
                    return null;
                    }
                }
            rating.setName(discipline.getName()+"-"+group.getName());
            long oid = db.mongoDB.add(rating);
            int count = 0;
            for (SAStudent student : group.getStudents()) {
                if (student.getState() != Values.StudentStateNormal)
                    continue;
                SASemesterRating semesterRating = new SASemesterRating();
                semesterRating.setState(Values.UndefinedType);
                semesterRating.setSemesterRating(0);
                semesterRating.getStudent().setOid(student.getOid());
                semesterRating.getSAGroupRating().setOid(oid);
                long semOid = db.mongoDB.add(semesterRating);
                SAExamRating examRating = new SAExamRating();
                examRating.setState(Values.StudRatingNotAllowed);
                examRating.setExcerciceRating(0);
                examRating.setQuestionRating(0);
                examRating.getStudent().setOid(student.getOid());
                examRating.getSAGroupRating().setOid(oid);
                examRating.getSemRating().setOid(semOid);
                db.mongoDB.add(examRating);
                count++;
                }
            return new JLong(oid);
            }
        };
    //-------------------------------------------------------------------------------------------------
    private GroupRatingReport create0(Request req, Response res) throws Exception{
        ParamLong ratingId = new ParamLong(req, res, "ratingId");
        if (!ratingId.isValid()) return null;
        SAGroupRating rating = new SAGroupRating();
        if (!db.mongoDB.getById(rating, ratingId.getValue(), 2)) {
            db.createHTTPError(res, ValuesBase.HTTPRequestError, "Рейтинг группы  id=" + ratingId.getValue() + " не найден");
            return null;
        }
        SAGroup group = new SAGroup();
        if (!db.mongoDB.getById(group, rating.getGroup().getOid(), 2)) {
            db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа  id=" + rating.getGroup().getOid() + " не найдена");
            return null;
        }
        rating.getGroup().setRef(group);
        SADiscipline discipline = new SADiscipline();
        if (!db.mongoDB.getById(discipline, rating.getSADiscipline().getOid(), 2)) {
            db.createHTTPError(res, ValuesBase.HTTPRequestError, "Дисциплина  id=" + rating.getSADiscipline().getOid() + " не найдена");
            return null;
            }
        rating.getSADiscipline().setRef(discipline);
        GroupRatingReport report = new GroupRatingReport(rating);
        return report;
        }
    RouteWrap apiCreateGroupReportArtifact = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            GroupRatingReport report = create0(req,res);
            ParamInt filetype = new ParamInt(req, res, "filetype");
            if (!filetype.isValid()) return null;
            boolean bb = db.common.createReportArtifact(res,report, filetype.getValue());
            return  bb ? report.reportFile : null;
            }
        };
    RouteWrap apiCreateGroupReportTable = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            GroupRatingReport report = create0(req,res);
            TableData data = new TableData();
            report.createReportFile(data,"");
            return data;
            }
        };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiRemoveGroupRating = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong ratingId = new ParamLong(req, res, "ratingId");
            if (!ratingId.isValid()) return null;
            SAGroupRating rating = new SAGroupRating();
            if (!db.mongoDB.getById(rating, ratingId.getValue(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Рейтинг группы  id=" + ratingId.getValue() + " не найден");
                return null;
                }
            SAGroup group = new SAGroup();
            if (!db.mongoDB.getById(group, rating.getGroup().getOid(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа id=" + rating.getGroup().getOid() + " не найдена");
                return null;
                }
            SADiscipline discipline = new SADiscipline();
            if (!db.mongoDB.getById(discipline, rating.getSADiscipline().getOid(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Дисциплина id=" + rating.getSADiscipline().getOid() + " не найдена");
                return null;
                }
            for(SAExamTaking taking : discipline.getTakings()){
                if (taking.isOneGroup() && taking.getGroup().getOid()==group.getOid()){
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа " + group.getName() + " назначена на экзамен");
                    return null;
                    }
                }
            EntityRefList<SAStudent> students = group.getStudents();
            students.createMap();
            for (SAExamRating ticket : rating.getExamRatings()) {
                if (students.getById(ticket.getStudent().getOid()) == null)
                    continue;
                if (!ticket.enableToRemove()) {
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа уже сдает экзамен (назначена)");
                    return null;
                    }
                }
            int count = 0;
            for (SAExamRating ticket : rating.getExamRatings()) {
                if (students.getById(ticket.getStudent().getOid()) == null)
                    continue;
                db.mongoDB.remove(ticket);
                count++;
                }
            ArrayList<Entity> teachers = db.mongoDB.getAll(new SATeacher());    // Удалить разрешения на рейтинг
            for(Entity ee : teachers){
                SATeacher teacher = (SATeacher)ee;
                if (teacher.getRatings().removeById(ratingId.getValue()))
                    db.mongoDB.update(teacher);
                }
            db.mongoDB.remove(rating);
            return new JEmpty();
            }
        };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiSetTakingForAll = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong takingId = new ParamLong(req, res, "takingId");
            if (!takingId.isValid()) return null;
            SAExamTaking taking = new SAExamTaking();
            if (!db.mongoDB.getById(taking, takingId.getValue(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Прием экзамена  id=" + takingId.getValue() + " не найден");
                return null;
                }
            SADiscipline discipline = new SADiscipline();
            if (!db.mongoDB.getById(discipline, taking.getSADiscipline().getOid(),1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Дисциплина id=" + taking.getSADiscipline().getOid() + " не найдена");
                return null;
                }
            discipline.createMaps();
            int count=0;
            for(SAGroupRating rating : discipline.getRatings()){
                if (taking.isOneGroup() && taking.getGroup().getOid()!=rating.getGroup().getOid())
                    continue;
                db.mongoDB.getById(rating,rating.getOid(),1);
                for(SAExamRating studRating : rating.getExamRatings())
                    if (studRating.getState()==Values.StudRatingAllowed){
                        studRating.setState(Values.StudRatingTakingSet);
                        studRating.getSAExamTaking().setOid(taking.getOid());
                        db.mongoDB.update(studRating);
                        count++;
                    }
                }
            return new JInt(count);
            }
        };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiGetGroupRatings = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong ratingId = new ParamLong(req, res, "ratingId");
            if (!ratingId.isValid()) return null;
            SAGroupRating rating = new SAGroupRating();
            if (!db.mongoDB.getById(rating, ratingId.getValue(), 1)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Рейтинг группы  id=" + ratingId.getValue() + " не найден");
                return null;
                }
            SAGroup group = new SAGroup();
            if (!db.mongoDB.getById(group, rating.getGroup().getOid(), 2)) {
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Группа id=" + rating.getGroup().getOid() + " не найдена");
                return null;
                }
            EntityRefList<SAStudent> students = group.getStudents();
            students.createMap();
            for (SAExamRating ticket : rating.getExamRatings()) {
                SAStudent student = students.getById(ticket.getStudent().getOid());
                if (student == null) {
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Студент id=" + ticket.getStudent().getOid() + " не найден");
                    return null;
                    }
                ticket.getStudent().setOidRef(student);
                }
            return rating;
            }
        };
    //------------------------------------------------------------------------------------------------
    RouteWrap apiGetTakingRatings = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            ParamLong takingId = new ParamLong(req, res, "takingId");
            if (!takingId.isValid()) return null;
            SAExamTaking examTaking = new SAExamTaking();
            if (!db.mongoDB.getById(examTaking,takingId.getValue(),1)){
                db.createHTTPError(res, ValuesBase.HTTPRequestError, "Прием экзамена id=" + takingId.getValue() + " не найден");
                return null;
                }
            for (SAExamRating ticket : examTaking.getRatings()) {
                SAStudent student = new SAStudent();
                if (!db.mongoDB.getById(student,ticket.getStudent().getOid(),1))
                if (student == null) {
                    db.createHTTPError(res, ValuesBase.HTTPRequestError, "Студент id=" + ticket.getStudent().getOid() + " не найден");
                    return null;
                    }
                ticket.getStudent().setOidRef(student);
                }
            return examTaking;
        }
        };
    public EntityList<Entity> getByCondition(Entity entity, String fname, String value, int level) throws UniException {
    List<BasicDBObject> obj = new ArrayList<BasicDBObject>();
        obj.add(new BasicDBObject(fname, value));
        obj.add(new BasicDBObject("valid",true));
        BasicDBObject query = new BasicDBObject();
        query.put("$and", obj);
        EntityList<Entity> zz = db.mongoDB.getAllByQuery(entity,query,level);
        return zz;
        }
    //------------------------------------------------------------------------------------------------
    public EntityList<Entity> getByCondition(Entity entity, String fname, long value, int level) throws UniException {
        List<BasicDBObject> obj = new ArrayList<BasicDBObject>();
        obj.add(new BasicDBObject(fname, value));
        obj.add(new BasicDBObject("valid",true));
        BasicDBObject query = new BasicDBObject();
        query.put("$and", obj);
        EntityList<Entity> zz = db.mongoDB.getAllByQuery(entity,query,level);
        return zz;
        }
    //------------------------------------------------------------------------------------------------
    public EntityList<Entity> getByCondition(Entity entity, String fname, int value, int level) throws UniException {
        List<BasicDBObject> obj = new ArrayList<BasicDBObject>();
        obj.add(new BasicDBObject(fname, value));
        obj.add(new BasicDBObject("valid",true));
        BasicDBObject query = new BasicDBObject();
        query.put("$and", obj);
        EntityList<Entity> zz = db.mongoDB.getAllByQuery(entity,query,level);
        return zz;
        }
    public EntityList<Entity> getByName(Entity entity, String value, int level) throws UniException {
        return getByCondition(entity,"name",value,level);
        }
    RouteWrap apiSelectMeasures = new RouteWrap() {
        @Override
        public Object _handle(Request req, Response res, RequestStatistic statistic) throws Exception {
            List<BasicDBObject> obj = new ArrayList<BasicDBObject>();
            ParamInt note = new ParamInt(req, res, "note",0);
            if (!note.isValid())
                return null;
            if (note.getValue()!=0)
                obj.add(new BasicDBObject("expertResult", note.getValue()));
            ParamLong userId = new ParamLong(req, res, "userId",0);
            if (!userId.isValid())
                return null;
            if (userId.getValue()!=0)
                obj.add(new BasicDBObject("userId", userId.getValue()));
            ParamString line = new ParamString(req,res,"line","");
            if (!line.isValid())
                return null;
            if (line.getValue().length()!=0)
                obj.add(new BasicDBObject("powerLineName",line.getValue()));
            ParamString support = new ParamString(req,res,"support","");
            if (!support.isValid())
                return null;
            if (support.getValue().length()!=0)
                obj.add(new BasicDBObject("supportName",support.getValue()));
            obj.add(new BasicDBObject("valid",true));
            BasicDBObject query = new BasicDBObject();
            query.put("$and", obj);
            EntityList<Entity> zz = db.mongoDB.getAllByQuery(new MeasureFile(),query,1);
            return zz;
        }
    };
     */
}


