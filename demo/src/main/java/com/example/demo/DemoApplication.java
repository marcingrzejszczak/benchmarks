package com.example.demo;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import brave.Tracer;
import brave.Tracing;
import brave.handler.SpanHandler;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.Sampler;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

//@SpringBootApplication
public class DemoApplication {

	static {
		Logger.getLogger(DemoApplication.class.getName()).setLevel(Level.OFF);
	}

	static class Brave {

		public static void main(String[] args) {

			int count = 10_000_000;

			BraveState braveState = new BraveState();
			braveState.setup();

			DemoApplication benchmark = new DemoApplication();
			Logger.getLogger(Tracer.class.getName()).setLevel(Level.OFF);
			try {
				System.in.read();
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}

			for (int counter = 0; counter < count; counter++) {
//				benchmark.braveTracing(braveState, counter);
				benchmark.braveNewSpan(braveState, counter);
			}

			Logger.getLogger(DemoApplication.class.getName()).log(Level.INFO, benchmark.toString());
		}

	}

	static class OTel {

		public static void main(String[] args) {

			int count = 10_000_000;

			OtelState otelState = new OtelState();
			otelState.setup();

			DemoApplication benchmark = new DemoApplication();


			try {
				System.in.read();
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}

			for (int counter = 0; counter < count; counter++) {
//				benchmark.otelTracing(otelState, counter);
				benchmark.otelNewSpan(otelState, counter);
			}

			Logger.getLogger(DemoApplication.class.getName()).log(Level.INFO, benchmark.toString());
		}

	}

	public static class BraveState {

		public int childSpanCount = 5;

		ThreadLocalCurrentTraceContext braveCurrentTraceContext;

		Tracing tracing;

		brave.Tracer tracer;


		public void setup() {
			this.braveCurrentTraceContext = ThreadLocalCurrentTraceContext.newBuilder().build();
			this.tracing = Tracing.newBuilder()
					.currentTraceContext(this.braveCurrentTraceContext)
					.sampler(Sampler.NEVER_SAMPLE)
					.addSpanHandler(SpanHandler.NOOP)
					.build();
			this.tracing.setNoop(true);
			this.tracer = this.tracing.tracer();
		}

		public void close() {
			this.tracing.close();
		}

	}

	public void braveTracing(BraveState state, int counter) {
		brave.Span parentSpan = state.tracer.nextSpan().name("parent-span" + counter).start();
		TraceContextOrSamplingFlags traceContext = TraceContextOrSamplingFlags.create(parentSpan.context());
		for (int i = 0; i < state.childSpanCount; i++) {
			brave.Span span = state.tracer.nextSpan(traceContext).name(counter + "new-span" + i);
			span.start().tag("key", "value").annotate("event").finish();
		}
		parentSpan.finish();
	}

	public void braveNewSpan(BraveState state, int counter) {
		state.tracer.nextSpan().name("new-span" + counter).start().finish();
	}

	public void braveTracingWithScope(BraveState state) {
		brave.Span parentSpan = state.tracer.nextSpan().name("parent-span").start();
		try (brave.Tracer.SpanInScope spanInScope = state.tracer.withSpanInScope(parentSpan)) {
			for (int i = 0; i < state.childSpanCount; i++) {
				brave.Span childSpan = state.tracer.nextSpan().name("new-span" + i);
				try (brave.Tracer.SpanInScope spanInScope2 = state.tracer.withSpanInScope(childSpan.start())) {
					childSpan.tag("key", "value").annotate("event");
				}
				childSpan.finish();
			}
		}
		parentSpan.finish();
	}

	public static class OtelState {

		public int childSpanCount = 5;

		SdkTracerProvider sdkTracerProvider;

		OpenTelemetrySdk openTelemetrySdk;

		io.opentelemetry.api.trace.Tracer tracer;

		io.opentelemetry.api.trace.Span startedSpan;

		Context parentContext;

		public void setup() {
			this.sdkTracerProvider = SdkTracerProvider.builder()
					.setSampler(io.opentelemetry.sdk.trace.samplers.Sampler.alwaysOff())
					.build();
			this.openTelemetrySdk = OpenTelemetrySdk.builder()
					.setTracerProvider(sdkTracerProvider)
					.build();
			this.tracer = openTelemetrySdk.getTracerProvider()
					.get("io.micrometer.micrometer-tracing");
			this.startedSpan = this.tracer.spanBuilder("started-span").startSpan();
			this.parentContext = Context.root().with(this.startedSpan);
		}

	}


	public void otelTracing(OtelState state, int counter) {
		io.opentelemetry.api.trace.Span parentSpan = state.tracer.spanBuilder("parent-span" + counter).startSpan();
		Context parentContext = Context.root().with(parentSpan);
		for (int i = 0; i < state.childSpanCount; i++) {
			io.opentelemetry.api.trace.Span span = state.tracer.spanBuilder(counter + "new-span" + i).setParent(parentContext).startSpan();
			span.setAttribute("key", "value").addEvent("event").end();
		}
		parentSpan.end();
	}

	public void otelNewSpan(OtelState state, int counter) {
		state.tracer.spanBuilder("new-span" + counter).startSpan().end();
	}

	public void otelTracingWithScope(OtelState state) {
		io.opentelemetry.api.trace.Span parentSpan = state.tracer.spanBuilder("parent-span").startSpan();
		try (io.opentelemetry.context.Scope scope = parentSpan.makeCurrent()) {
			for (int i = 0; i < state.childSpanCount; i++) {
				io.opentelemetry.api.trace.Span span = state.tracer.spanBuilder("new-span" + i).startSpan();
				try (io.opentelemetry.context.Scope scope2 = span.makeCurrent()) {
					span.setAttribute("key", "value").addEvent("event");
				}
				span.end();
			}
		}
		parentSpan.end();
	}
}
