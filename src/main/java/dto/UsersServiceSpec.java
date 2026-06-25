package dto;

public class UsersServiceSpec {
	public String role;
	public String username;
	public String password;

	public UsersServiceSpec() {
	}

	public UsersServiceSpec(String role, String username, String password) {
		this.role = role;
		this.username = username;
		this.password = password;
	}
}
