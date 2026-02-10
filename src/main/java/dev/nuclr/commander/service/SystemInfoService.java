package dev.nuclr.commander.service;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import lombok.Data;
import oshi.SystemInfo;

@Service
@Data
@Lazy
public final class SystemInfoService {

	@Data
	public static final class SystemData {

		private String osFamily;
		private int osBitness;
		private String osManufacturer;
		private String osBuildNumber;
		private String osCodeName;
		private String osVersion;

		private String cpuFamily;
		private String cpuIdentifier;
		private String cpuMicroarchitecture;
		private String cpuModel;
		private String cpuName;
		private String cpuVendor;

	}

	public SystemData getSystemInfo() {

		SystemInfo si = new SystemInfo();

		var hw = si.getHardware();
		var os = si.getOperatingSystem();
		var cpu = hw.getProcessor();

		var info = new SystemData();
		info.setOsFamily(os.getFamily());
		info.setOsBitness(os.getBitness());
		info.setOsManufacturer(os.getManufacturer());
		info.setOsBuildNumber(os.getVersionInfo().getBuildNumber());
		info.setOsCodeName(os.getVersionInfo().getCodeName());
		info.setOsVersion(os.getVersionInfo().getVersion());
		info.setCpuFamily(cpu.getProcessorIdentifier().getFamily());
		info.setCpuIdentifier(cpu.getProcessorIdentifier().getIdentifier());
		info.setCpuMicroarchitecture(cpu.getProcessorIdentifier().getMicroarchitecture());
		info.setCpuModel(cpu.getProcessorIdentifier().getModel());
		info.setCpuName(cpu.getProcessorIdentifier().getName());
		info.setCpuVendor(cpu.getProcessorIdentifier().getVendor());

		return info;
	}

	public static void main(String[] args) {

		var service = new SystemInfoService();
		var info = service.getSystemInfo();

		System.out
				.println(ToStringBuilder.reflectionToString(info, ToStringStyle.MULTI_LINE_STYLE));

	}

}
