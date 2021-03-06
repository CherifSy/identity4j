package com.identity4j.connector.office365;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.identity4j.connector.AbstractConnector;
import com.identity4j.connector.ConnectorCapability;
import com.identity4j.connector.ConnectorConfigurationParameters;
import com.identity4j.connector.exception.ConnectorException;
import com.identity4j.connector.exception.PrincipalAlreadyExistsException;
import com.identity4j.connector.exception.PrincipalNotFoundException;
import com.identity4j.connector.office365.entity.Group;
import com.identity4j.connector.office365.entity.Groups;
import com.identity4j.connector.office365.entity.User;
import com.identity4j.connector.office365.entity.Users;
import com.identity4j.connector.office365.services.Directory;
import com.identity4j.connector.principal.Identity;
import com.identity4j.connector.principal.Role;
import com.identity4j.util.CollectionUtil;
import com.identity4j.util.passwords.PasswordCharacteristics;

/**
 * Office 365 connector makes use of Active Directory Graph REST API to perform admin operations.
 * Connector enables CRUD operations on Users and can map them to Groups.
 * Users and Groups are referred as Office365Identity and Role respectively in identity4j domain.
 * 
 * <p>
 * To map properties of User not supported by Office365Identity we can make use attributes map.
 * <pre>
 * E.g. role.setAttribute("email", group.getEmail());
 * </pre>
 * Here we are using attribute map with key "email" to store email id which is not a property in role.
 * </p>
 * 
 * <p>
 * The API can be referred from <a href="http://msdn.microsoft.com/en-us/library/hh974482.aspx">Active Directory Graph REST API</a>
 * </p>
 * 
 * @author gaurav
 *
 */
public class Office365Connector extends AbstractConnector {


	private Office365Configuration configuration;
	private Directory directory;
	private static final Log log = LogFactory.getLog(Office365Connector.class);
	private boolean isDeletePrivilege;
	
	static Set<ConnectorCapability> capabilities = new HashSet<ConnectorCapability>(Arrays.asList(new ConnectorCapability[] { 
			ConnectorCapability.passwordChange,
			ConnectorCapability.passwordSet,
			ConnectorCapability.createUser,
			ConnectorCapability.deleteUser,
			ConnectorCapability.updateUser,
			ConnectorCapability.hasFullName,
			ConnectorCapability.hasEmail,
			ConnectorCapability.roles,
			ConnectorCapability.createRole,
			ConnectorCapability.deleteRole,
			ConnectorCapability.updateRole,
			ConnectorCapability.authentication,
			ConnectorCapability.identities,
			ConnectorCapability.accountDisable
	}));

	@Override
	public Set<ConnectorCapability> getCapabilities() {
		return capabilities;
	}

	/**
	 * Check to see if connector is open
	 */
	@Override
	public boolean isOpen() {
		return directory != null;
	}

	/**
	 * Check to see connector if in read only mode
	 */
	@Override
	public boolean isReadOnly() {
		return !isDeletePrivilege;
	}
	

	@Override
	public PasswordCharacteristics getPasswordCharacteristics() {
		return Office365PasswordCharacteristics.getInstance();
	}

	/**
	 * <p>
	 * Retrieves all the roles (groups) present in the active directory.
	 * <br/>
	 * <b>Note:</b> Role in active directory is referred as Groups and identified by only guid, not group name.
	 * <br/>
	 * <b>Refer groups by guid as all operations on groups are performed using only guid.</b>
	 * <br/>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a>.
	 * </p>
	 * 
	 * @throws ConnectorException for api, connection related errors.
	 */
	@Override
	public Iterator<Role> allRoles() throws ConnectorException {
		Groups groups = directory.groups().all();
		List<Role> roles = new ArrayList<Role>();
		for (Group group : groups.getGroups()) {
			roles.add(Office365ModelConvertor.groupToRole(group));
		}
		return roles.iterator();
	}
	
	/**
	 * <p>
	 * Creates a role in the active directory.
	 * <br/>
	 * <b>Note:</b> Role in active directory is referred as Groups and identified by only guid, not group name.
	 * <br/>
	 * <b>Refer groups by guid as all operations on groups are performed using only guid.</b>
	 * <br/>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a>.
	 * </p>
	 * 
	 * @throws PrincipalAlreadyExistsException if role with same email id/principal already present in active directory.
	 * @throws ConnectorException for api, connection related errors.
	 */
	@Override
	public Role createRole(Role role) throws ConnectorException {
		//we have to check this REST api does not throws exception for same principal name and we cannot fetch role by name
		if(isRolePresent(role.getPrincipalName())){
			throw new PrincipalAlreadyExistsException("Principal contains conflicting properties which already exists, " + role.getPrincipalName());
		}
		Group group = Office365ModelConvertor.roleToGroup(role);
		return Office365ModelConvertor.groupToRole(directory.groups().save(group));
	}

	
	/**
	 * <p>
	 * Updates a role in the active directory with specified changes.
	 * <br/>
	 * <b>Note:</b> Role in active directory is referred as Groups and identified by only guid, not group name.
	 * <br/>
	 * <b>Refer groups by guid as all operations on groups are performed using only guid.</b>
	 * <br/>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a>.
	 * </p>
	 * 
	 * @throws PrincipalNotFoundException if role specified by email id/principal name not present in active directory.
	 * @throws ConnectorException for api, connection related errors or delete privilege not given to service id.
	 */
	@Override
	public void updateRole(Role role) throws ConnectorException {
		if(isReadOnly()){
			throw new ConnectorException("This directory is read only because the service account does not have sufficient privileges to perform all required operations");
		}
		Group group = Office365ModelConvertor.roleToGroup(role);
		directory.groups().update(group);
	}

	
	/**
	 * <p>
	 * Deletes a role in the active directory with specified principal name.
	 * <br/>
	 * <b>Note:</b> Role in active directory is referred as Groups and identified by only guid, not group name.
	 * <br/>
	 * <b>Refer groups by guid as all operations on groups are performed using only guid.</b>
	 * <br/>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a>.
	 * </p>
	 * 
	 * @throws PrincipalNotFoundException if role specified by email id/principal name not present in active directory.
	 * @throws ConnectorException for api, connection related errors or delete privilege not given to service id.
	 */
	@Override
	public void deleteRole(String guid) throws ConnectorException {
		if(isReadOnly()){
			throw new ConnectorException("This directory is read only because the service account does not have sufficient privileges to perform all required operations");
		}
		Role role = getRoleByName(guid);
		directory.groups().delete(role.getGuid());
	}
	
	/**
	 * <p>
	 * Finds a role in the active directory with specified principal name.
	 * <br/>
	 * <b>Note:</b> Role in active directory is referred as Groups and identified by only guid, not group name.
	 * <br/>
	 * <b>Refer groups by guid as all operations on groups are performed using only guid.</b>
	 * <br/>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a>.
	 * </p>
	 * 
	 * @throws PrincipalNotFoundException if role specified by email id/principal name not present in active directory.
	 * @throws ConnectorException for api, connection related errors.
	 */
	@Override
	public Role getRoleByName(String guid) throws PrincipalNotFoundException,
			ConnectorException {
		Group group = directory.groups().get(guid);
		return Office365ModelConvertor.groupToRole(group);
	}
	
	/**
	 * <p>
	 * Finds all identities present in active directory.
	 * <p>
	 * The extra attributes supported by graph e.g. department, postalCode are populated in identites's attributes map.
	 * <pre>
	 * identity.getAttribute("department")
	 * </pre>
	 * </p>
	 * 
	 * <p>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a> and
	 * <a href="http://msdn.microsoft.com/en-us/library/dn130116.aspx">User</a>
	 * </p>
	 * 
	 * 
	 * @throws ConnectorException for api, connection related errors.
	 * 
	 * @return iterator with all identities.
	 */
	@Override
	public Iterator<Identity> allIdentities() throws ConnectorException {
		return new Iterator<Identity>() {
			
			private Users users;
			private String nextLink;
			private Iterator<User> inner;
			private User current;
			private boolean eof;

			@Override
			public boolean hasNext() {
				checkNext();
				return current != null;
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Identity next() {
				checkNext();
				if(current == null) 
					throw new NoSuchElementException();
				try {
					return Office365ModelConvertor.convertOffice365UserToOfficeIdentity(current);
				}
				finally {
					current = null;
				}
			}
			
			private void checkNext() {
				if(current != null)
					// Already have an unconsumed user
					return;
				
				while(!eof) {
					if(users == null) {
						// Get the next batch
						users = directory.users().all(nextLink);
						nextLink = users.getNextLink();
						eof = nextLink == null;
						inner = users.getUsers().iterator();
					}
					
					if(inner.hasNext()) {
						// We now have a user
						break;
					}
					
					// Finished inner iterator, 
					users = null;
					inner = null;
					
					if(nextLink == null) {
						// No more 
						break;
					}
				}
				
				if(inner != null && inner.hasNext())
					current = inner.next();
			}
			
		};
	}
	
	/**
	 * <p>
	 * Finds an identity by principal/email id supplied.
	 * <p>
	 * The extra attributes supported by graph e.g. department, postalCode are populated in identites's attributes map.
	 * <pre>
	 * identity.getAttribute("department")
	 * </pre>
	 * </p>
	 * 
	 * <p>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a> and
	 * <a href="http://msdn.microsoft.com/en-us/library/dn130116.aspx">User</a>
	 * </p>
	 * 
	 * @throws PrincipalNotFoundException if identity specified by email id/principal name not present in active directory.
	 * @throws ConnectorException for api, connection related errors.
	 * 
	 * @return Identity instance found by the specified email id/principal name.
	 */
	@Override
	public Identity getIdentityByName(String name)
			throws PrincipalNotFoundException, ConnectorException {
		User user = directory.users().get(name);
		return Office365ModelConvertor.convertOffice365UserToOfficeIdentity(user);
	}
	
	/**
	 * <p>
	 * Deletes an identity in active directory.
	 * </>
	 * 
	 * <p>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a> and
	 * <a href="http://msdn.microsoft.com/en-us/library/dn130116.aspx">User</a>
	 * </p>
	 * 
	 * @throws PrincipalNotFoundException if identity specified by email id/principal name not present in active directory.
	 * @throws ConnectorException for api, connection related errors or delete privilege not given to service id.
	 */
	@Override
	public void deleteIdentity(String principalName) throws ConnectorException {
		if(isReadOnly()){
			throw new ConnectorException("This directory is read only because the service account does not have sufficient privileges to perform all required operations");
		}
		directory.users().delete(principalName);
	}
	
	/**
	 * <p>
	 * Creates identity with specified roles and password provided.
	 * Role in Graph api is known as Group and is identified by unique guid.
	 * Role guid is auto generated number from Graph API.
	 * </p>
	 * 
	 * <p>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a> and
	 * <a href="http://msdn.microsoft.com/en-us/library/dn130116.aspx">User</a>
	 * </p>
	 * 
	 * <p>
	 * The extra attributes supported by graph e.g. department, postalCode are populated in identites's attributes map.
	 * <pre>
	 * identity.getAttribute("department")
	 * </pre>
	 * </p>
	 * 
	 * @throws PrincipalAlreadyExistsException if identity with same email id/principal already present in active directory.
	 * @throws ConnectorException for api, connection related errors.
	 * 
	 * @return Identity instance with values specified for creation.
	 */
	@Override
	public Identity createIdentity(Identity identity, char[] password)
			throws ConnectorException {
		User user = Office365ModelConvertor.covertOfficeIdentityToOffice365User(identity);
		List<Group> groups = user.getGroups();
		user.setGroups(null);//as groups will be saved independent from User
		user.getPasswordProfile().setForceChangePasswordNextLogin(false);
		user.getPasswordProfile().setPassword(new String(password));
		Identity identitySaved =  Office365ModelConvertor.convertOffice365UserToOfficeIdentity(directory.users().save(user));
		for (Group group : groups) {
			directory.groups().addUserToGroup(identitySaved.getGuid(),group.getObjectId());
		}
		identitySaved.setRoles(identity.getRoles());
		return identitySaved;
	}
	
	
	/**
	 * <p>
	 * Updates an identity in active directory.
	 * To update extra attributes supported by active directory, use attributes map.
	 * <pre>
	 * e.g. identity.setAttribute("department","engineering")
	 * </pre>
	 * </p>
	 * 
	 *  <p>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a> and
	 * <a href="http://msdn.microsoft.com/en-us/library/dn130116.aspx">User</a>
	 * </p>
	 * 
	 * @throws PrincipalNotFoundException if identity specified by email id/principal name not present in active directory.
	 * @throws ConnectorException for api, connection related errors or delete privilege not given to service id.
	 */
	@Override
	public void updateIdentity(Identity identity) throws ConnectorException {
		if(isReadOnly()){
			throw new ConnectorException("This directory is read only because the service account does not have sufficient privileges to perform all required operations");
		}
		User user = Office365ModelConvertor.covertOfficeIdentityToOffice365User(identity);
		user.setGroups(null);//will be updated individually not along with user
		directory.users().update(user);
		adjustAdditionRemovalOfRoleOnIdentityUpdate(identity);
	}
	
	
	/**
	 * Disables/Suspends an account in active directory.
	 * 
	 * @throws PrincipalNotFoundException if identity specified by email id/principal name not present in active directory.
	 * @throws ConnectorException for api, connection related errors.
	 */
	@Override
	public void disableIdentity(Identity identity) {
		identitySuspensionHelper(identity,true);
		identity.getAccountStatus().setDisabled(true);
	}
	
	/**
	 * Enables an account in active directory.
	 * 
	 * @throws PrincipalNotFoundException if identity specified by email id/principal name not present in active directory.
	 * @throws ConnectorException for api, connection related errors.
	 */
	@Override
	public void enableIdentity(Identity identity) {
		identitySuspensionHelper(identity,false);
		identity.getAccountStatus().setDisabled(false);
	}
	
	/**
	 * <p>
	 * Checks credential provided are valid or not.
	 * This method uses browser simulation using HTML Unit library to simulate oAuth authorization process, as it happens in a browser.
	 * <br />
	 * <strong>Note:</strong> This method depends on sign in html page returned for active directory, hence has external dependency
	 * on how html page is returned, any change in returned html might break the method.
	 * </p>
	 * 
	 * @throws ConnectorException for api, connection related errors.
	 * 
	 * @return true if credentials are correct else false
	 */
	@Override
	protected boolean areCredentialsValid(Identity identity, char[] password)
			throws ConnectorException{
		return directory.users().areCredentialsValid(identity, password);
	}
	
	
	/**
	 * Changes the password of the identity specified by email id/principal.
	 * Also provides provision to force change password in next logon attempt.
	 * 
	 * 
	 * @throws ConnectorException for api, connection related errors.
	 */
	@Override
	protected void setPassword(Identity identity, char[] password, boolean forcePasswordChangeAtLogon) throws ConnectorException {
		User user = new User();
		user.getPasswordProfile().setPassword(new String(password));
		user.getPasswordProfile().setForceChangePasswordNextLogin(forcePasswordChangeAtLogon);
		user.setObjectId(identity.getGuid());
		directory.users().update(user);
	}
	
	/**
	 * Helper utility method to enable/disable suspension for an identity
	 * 
	 * @param identity
	 * @param suspension true to suspend an account, false to enable it
	 * 
	 * @throws PrincipalNotFoundException if identity specified by email id/principal name not present in active directory.
	 * @throws ConnectorException for api, connection related errors.
	 */
	private void identitySuspensionHelper(Identity identity,boolean suspension) {
		User user = new User();
		user.setAccountEnabled(!suspension);
		user.setObjectId(identity.getGuid());
		directory.users().update(user);
	}

	
	/**
	 * <p>
	 * Creates directory instance for remote method invocations using Active Directory Graph REST API.
	 * The directory instance is created by providing and enabling
	 * <ul>
	 * 	<li>Giving read,write and delete access to service id, it should have role similar to "User Account Administrator" in the active directory.</li>
	 *  <li>Secret key of the service id.</li>
	 *  <li>Enabling API Access</li>
	 * </ul>
	 * </p>
	 */
	@Override
	protected void onOpen(ConnectorConfigurationParameters parameters)
			throws ConnectorException {
		configuration = (Office365Configuration) parameters;
		
		directory = Directory.getInstance();
		
		log.info("Directory instance created.");
		try {
			directory.init(configuration);
			isDeletePrivilege = directory.users().isDeletePrivilege(configuration.getAppPrincipalObjectId(),configuration.getAppDeletePrincipalRole());
			log.info("Delete privilege found as " + isDeletePrivilege);
		} catch (IOException e) {
			throw new ConnectorException(e.getMessage(), e);
		}

	}
	
	/**
	 * Helper utility method to adjust addition and removal of roles from an identity.
	 * It compares the roles currently assigned and new set of roles sent and finds which are to be added and which are to 
	 * be removed and accordingly performs removal or addition action.
	 * 
	 * @param identity
	 * 
	 * @throws ConnectorException for api, connection related errors.
	 */
	private void adjustAdditionRemovalOfRoleOnIdentityUpdate(Identity identity){
		try{
			Identity identityFromSource = getIdentityByName(identity.getPrincipalName());
			
			Set<Role> rolesCurrentlyAssigned = new HashSet<Role>(Arrays.asList(identityFromSource.getRoles()));
			Set<Role> rolesToBeAssigned = new HashSet<Role>(Arrays.asList(identity.getRoles()));
			
			Collection<Role> newRolesToAdd = CollectionUtil.objectsNotPresentInProbeCollection(rolesToBeAssigned, rolesCurrentlyAssigned);
			Collection<Role> rolesToRemove = CollectionUtil.objectsNotPresentInProbeCollection(rolesCurrentlyAssigned,rolesToBeAssigned);
			
			for (Role role : newRolesToAdd) {
				addRoleToUser(role.getGuid(), identity.getGuid());
			}
			
			for (Role role : rolesToRemove) {
				removeRoleFromUser(role.getGuid(), identity.getGuid());
			}
		}catch(Exception e){
			log.error("Problem in adjusting roles " + e.getMessage(), e);
			throw new ConnectorException(e.getMessage(), e);
		}
	}

	
	/**
	 * Removes a Role(Group) from an identity.
	 * <br/>
	 * <b>Refer groups by guid as all operations on groups are performed using only guid.</b>
	 * <br/>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a>.
	 * </p>
	 * 
	 * @param roleName
	 * @param principal
	 * 
	 * @throws ConnectorException for api, connection related errors.
	 */
	private void removeRoleFromUser(String guidRole, String guidUser) {
		directory.groups().removeUserFromGroup(guidUser,guidRole);
		
	}

	/**
	 * Adds a Role(Group) to an identity.
	 * <br/>
	 * <b>Refer groups by guid as all operations on groups are performed using only guid.</b>
	 * <br/>
	 * Please refer <a href="http://msdn.microsoft.com/en-us/library/dn151610.aspx">Groups</a>.
	 * </p>
	 * 
	 * @param roleName
	 * @param principal
	 * 
	 * @throws ConnectorException for api, connection related errors.
	 */
	private void addRoleToUser(String guidRole, String guidUser) {
		directory.groups().addUserToGroup(guidUser,guidRole);
		
	}
	
	/**
	 * Helper utility method which checks the presence of a Role in list of Roles
	 * @param roleName
	 * @return true if role is present else false
	 */
	private boolean isRolePresent(String roleName){
		Iterator<Role> roles = allRoles();
		Role role = null;
		while(roles.hasNext()){
			role = roles.next();
			if(role.getPrincipalName().equals(roleName)){
				return true;
			}
		}
		return false;
	}

}
