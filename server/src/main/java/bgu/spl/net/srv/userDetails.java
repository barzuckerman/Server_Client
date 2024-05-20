package bgu.spl.net.srv;

public class userDetails {
    private String username;
    private int connectionId;
    private boolean isLoggedIn;


    public userDetails(int connectionId){
    this.username = "";
    this.connectionId = connectionId;
    this.isLoggedIn = false;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public String getUsername() {
        return username;
    }
    public Boolean getLoggedIn(){
        return isLoggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        isLoggedIn = loggedIn;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
