package Gateway.protocol;

public class Response {
    private final int statusCode;

    private final String requestId;
    private final String serverName;
    private final String message;

    public Response(int statusCode, String requestId, String serverName, String message){
        this.statusCode=statusCode;
        this.requestId=requestId;
        this.serverName=serverName;
        this.message=message;
    }

    //Dong goi thanh chuoi gui qua Socket
    public String serialize(){
        return statusCode+"|"+requestId+"|"+serverName+"|"+message;
    }
    public static Response parse(String raw){
        if(raw==null||raw.trim().isEmpty()){
            return null;
        }
        String[] parts=raw.split("\\|",4);
        int code=500;
        try{
            code=Integer.parseInt(parts[0]);
        }catch(Exception ignored){}

        String id=parts.length>1?parts[1]:"";
        String sName=parts.length>2?parts[2]:"";
        String msg=parts.length>3?parts[3]:"";

        return new Response(code, id, sName, msg);
    }

    public int getStatusCode() {return statusCode; }
    public String getRequestId(){ return requestId; }
    public String getServerName() { return serverName; }
    public String getMessage() { return message; }

    public boolean isSuccess() { return statusCode == 200; }

    @Override 
    public String toString() {
        return "Response[Code=" + statusCode + ", ID=" + requestId + ", From=" + serverName + ", Msg=" + message + "]";
    }
}
