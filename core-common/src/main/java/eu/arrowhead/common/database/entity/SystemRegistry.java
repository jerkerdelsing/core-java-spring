package eu.arrowhead.common.database.entity;

import eu.arrowhead.common.CoreDefaults;
import eu.arrowhead.common.dto.shared.ServiceSecurityType;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"systemId", "deviceId"}))
public class SystemRegistry
{

	//=================================================================================================
	// members
	public static final List<String> SORTABLE_FIELDS_BY = List.of("id", "updatedAt", "createdAt"); //NOSONAR

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "systemId", referencedColumnName = "id", nullable = false)
	private System system;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "deviceId", referencedColumnName = "id", nullable = false)
	private Device device;

	@Column(nullable = true)
	private ZonedDateTime endOfValidity;

	@Column(nullable = true, columnDefinition = "TEXT")
	private String metadata;

	@Column(nullable = true)
	private Integer version = 1;

	@Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
	private ZonedDateTime createdAt;

	@Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
	private ZonedDateTime updatedAt;

	//=================================================================================================
	// methods

	//-------------------------------------------------------------------------------------------------
	public SystemRegistry() {}

	//-------------------------------------------------------------------------------------------------
	public SystemRegistry(final System system, final Device device, final ZonedDateTime endOfValidity,
                          final String metadata, final Integer version) {
		this.system = system;
		this.device = device;
		this.endOfValidity = endOfValidity;
		this.metadata = metadata;
		this.version = version;
	}
	
	//-------------------------------------------------------------------------------------------------
	@PrePersist
	public void onCreate() {
		this.createdAt = ZonedDateTime.now();
		this.updatedAt = this.createdAt;
	}
	
	//-------------------------------------------------------------------------------------------------
	@PreUpdate
	public void onUpdate() {
		this.updatedAt = ZonedDateTime.now();
	}

	//-------------------------------------------------------------------------------------------------
	public long getId() { return id; }
	public System getSystem() {	return system; }
	public Device getDevice() { return device; }
	public ZonedDateTime getEndOfValidity() { return endOfValidity; }
	public String getMetadata() { return metadata; }
	public Integer getVersion() { return version; }
	public ZonedDateTime getCreatedAt() { return createdAt; }
	public ZonedDateTime getUpdatedAt() { return updatedAt; }

	//-------------------------------------------------------------------------------------------------
	public void setId(final long id) { this.id = id; }
	public void setSystem(final System system) { this.system = system; }
	public void setDevice(final Device device) { this.device = device; }
	public void setEndOfValidity(final ZonedDateTime endOfValidity) { this.endOfValidity = endOfValidity; }
	public void setMetadata(final String metadata) { this.metadata = metadata; }
	public void setVersion(final Integer version) { this.version = version; }
	public void setCreatedAt(final ZonedDateTime createdAt) { this.createdAt = createdAt; }
	public void setUpdatedAt(final ZonedDateTime updatedAt) { this.updatedAt = updatedAt; }

	//-------------------------------------------------------------------------------------------------
	@Override
	public String toString() {
		return "ServiceRegistry [id = " + id + ", system = " + system + ", device = " + device + ", endOfValidity = " + endOfValidity + ", version = " + version + "]";
	}

	//-------------------------------------------------------------------------------------------------
	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	//-------------------------------------------------------------------------------------------------
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		
		if (obj == null) {
			return false;
		}
		
		if (getClass() != obj.getClass()) {
			return false;
		}
		
		final SystemRegistry other = (SystemRegistry) obj;
		
		return id == other.id;
	}
}