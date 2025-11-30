public class main {
    public static void main(String[] args) throws Exception {
        DALService dal=new DALService();
        BLLService bll = new BLLService();
        dal.start();
        System.out.println("DAL已经启动");
        bll.start();
        System.out.println("BLL已经启动");
    }
}
