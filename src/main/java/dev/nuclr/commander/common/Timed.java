package dev.nuclr.commander.common;

import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Timed {

	public static <T> T of(String msg, Supplier<T> supplier) throws RuntimeException {
		long startTime = System.currentTimeMillis();
		try {
			return supplier.get();
		} finally {
			long endTime = System.currentTimeMillis();
			System.out.println(msg + " took " + (endTime - startTime) + " ms");
		}
	}

}
