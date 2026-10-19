# The documentation site in a container, for machines without a local Python (see docs/Justfile).
# Mount the REPOSITORY ROOT at /repo: main.py reads ../build.sbt for the version shown on the home page.
FROM python:3.12-slim
COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt
WORKDIR /repo/docs
EXPOSE 8000
ENTRYPOINT ["zensical"]
CMD ["serve", "--dev-addr", "0.0.0.0:8000"]
