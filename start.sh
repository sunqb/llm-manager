#!/bin/bash

# ============================================================
# LLM Manager 启动脚本
# 支持: start | stop | restart | status
# ============================================================

# 配置
JAVA_HOME="/Volumes/samsungssd/soft/jdk-21.0.8.jdk/Contents/Home"
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$PROJECT_ROOT/llm-manager-parent"
FRONTEND_DIR="$PROJECT_ROOT/llm-manager-ui"

# PID 文件
PID_DIR="$PROJECT_ROOT/.pids"
BACKEND_PID_FILE="$PID_DIR/backend.pid"
FRONTEND_PID_FILE="$PID_DIR/frontend.pid"

# 日志文件
LOG_DIR="$PROJECT_ROOT/logs"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 创建必要目录
mkdir -p "$PID_DIR" "$LOG_DIR"

# ============================================================
# 辅助函数
# ============================================================

log_info() {
    printf "${BLUE}[INFO]${NC} %s\n" "$1"
}

log_success() {
    printf "${GREEN}[SUCCESS]${NC} %s\n" "$1"
}

log_warn() {
    printf "${YELLOW}[WARN]${NC} %s\n" "$1"
}

log_error() {
    printf "${RED}[ERROR]${NC} %s\n" "$1"
}

# 检查进程是否运行
is_running() {
    local pid_file=$1
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if ps -p "$pid" > /dev/null 2>&1; then
            return 0
        fi
    fi
    return 1
}

# 获取 PID
get_pid() {
    local pid_file=$1
    if [ -f "$pid_file" ]; then
        cat "$pid_file"
    fi
}

# 检查端口是否在监听
check_port() {
    local port=$1
    lsof -i :$port > /dev/null 2>&1
    return $?
}

# ============================================================
# 后端操作
# ============================================================

start_backend() {
    if is_running "$BACKEND_PID_FILE"; then
        log_warn "后端已在运行 (PID: $(get_pid $BACKEND_PID_FILE))"
        return 1
    fi

    # 检查端口是否被占用
    if check_port 8080; then
        log_warn "端口 8080 已被占用"
        return 1
    fi

    log_info "启动后端服务..."

    cd "$BACKEND_DIR"

    # 设置 JAVA_HOME
    export JAVA_HOME="$JAVA_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"

    # 先编译整个项目（确保依赖模块被安装到本地仓库）
    log_info "编译项目..."
    if ! mvn clean install -DskipTests -q; then
        log_error "项目编译失败，请检查代码"
        return 1
    fi
    log_success "项目编译成功"

    cd "$BACKEND_DIR/llm-ops"

    # 使用 Maven 启动（后台运行）
    nohup mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx512m" > "$BACKEND_LOG" 2>&1 &
    local pid=$!
    echo $pid > "$BACKEND_PID_FILE"

    log_info "等待后端启动 (PID: $pid)..."

    # 等待端口可用（最多等待 60 秒）
    local count=0
    local max_wait=60
    while [ $count -lt $max_wait ]; do
        if check_port 8080; then
            log_success "后端启动成功！"
            log_info "日志文件: $BACKEND_LOG"
            log_info "访问地址: http://localhost:8080"
            return 0
        fi

        # 检查进程是否还存活
        if ! ps -p $pid > /dev/null 2>&1; then
            log_error "后端进程已退出，请查看日志: $BACKEND_LOG"
            rm -f "$BACKEND_PID_FILE"
            return 1
        fi

        printf "."
        sleep 2
        count=$((count + 2))
    done

    printf "\n"
    log_warn "后端启动超时，但进程仍在运行 (PID: $pid)"
    log_info "请查看日志: tail -f $BACKEND_LOG"
    return 0
}

stop_backend() {
    if ! is_running "$BACKEND_PID_FILE"; then
        log_warn "后端未在运行"
        rm -f "$BACKEND_PID_FILE"

        # 尝试通过端口查找并停止
        if check_port 8080; then
            log_info "发现端口 8080 被占用，尝试停止..."
            local pids=$(lsof -t -i :8080 2>/dev/null)
            if [ -n "$pids" ]; then
                echo "$pids" | xargs kill 2>/dev/null
                sleep 2
            fi
        fi
        return 0
    fi

    local pid=$(get_pid "$BACKEND_PID_FILE")
    log_info "停止后端服务 (PID: $pid)..."

    # 先尝试优雅停止
    kill "$pid" 2>/dev/null

    # 同时停止 Maven 启动的 Java 进程
    pkill -P "$pid" 2>/dev/null

    # 等待进程结束
    local count=0
    while is_running "$BACKEND_PID_FILE" && [ $count -lt 30 ]; do
        sleep 1
        count=$((count + 1))
    done

    # 强制结束
    if is_running "$BACKEND_PID_FILE"; then
        log_warn "正常停止超时，强制结束..."
        kill -9 "$pid" 2>/dev/null
        pkill -9 -P "$pid" 2>/dev/null
    fi

    # 确保端口释放
    if check_port 8080; then
        local java_pids=$(lsof -t -i :8080 2>/dev/null)
        if [ -n "$java_pids" ]; then
            echo "$java_pids" | xargs kill -9 2>/dev/null
        fi
    fi

    rm -f "$BACKEND_PID_FILE"
    log_success "后端已停止"
}

# ============================================================
# 前端操作
# ============================================================

start_frontend() {
    if is_running "$FRONTEND_PID_FILE"; then
        log_warn "前端已在运行 (PID: $(get_pid $FRONTEND_PID_FILE))"
        return 1
    fi

    # 检查端口是否被占用
    if check_port 5173; then
        log_warn "端口 5173 已被占用"
        return 1
    fi

    log_info "启动前端服务..."

    cd "$FRONTEND_DIR"

    # 检查 node_modules
    if [ ! -d "node_modules" ]; then
        log_info "安装前端依赖..."
        npm install
    fi

    # 使用 nohup 启动 Vite
    nohup npm run dev > "$FRONTEND_LOG" 2>&1 &
    local pid=$!
    echo $pid > "$FRONTEND_PID_FILE"

    log_info "等待前端启动 (PID: $pid)..."

    # 等待端口可用
    local count=0
    while [ $count -lt 30 ]; do
        if check_port 5173; then
            log_success "前端启动成功！"
            log_info "日志文件: $FRONTEND_LOG"
            log_info "访问地址: http://localhost:5173"
            return 0
        fi
        sleep 1
        count=$((count + 1))
    done

    if is_running "$FRONTEND_PID_FILE"; then
        log_warn "前端启动超时，但进程仍在运行"
    else
        log_error "前端启动失败，请查看日志: $FRONTEND_LOG"
        rm -f "$FRONTEND_PID_FILE"
        return 1
    fi
}

stop_frontend() {
    if ! is_running "$FRONTEND_PID_FILE"; then
        log_warn "前端未在运行"
        rm -f "$FRONTEND_PID_FILE"
        return 0
    fi

    local pid=$(get_pid "$FRONTEND_PID_FILE")
    log_info "停止前端服务 (PID: $pid)..."

    # 结束进程组（包括子进程）
    pkill -P "$pid" 2>/dev/null
    kill "$pid" 2>/dev/null

    sleep 2

    # 强制结束
    if is_running "$FRONTEND_PID_FILE"; then
        kill -9 "$pid" 2>/dev/null
        pkill -9 -P "$pid" 2>/dev/null
    fi

    rm -f "$FRONTEND_PID_FILE"
    log_success "前端已停止"
}

# ============================================================
# 主要命令
# ============================================================

do_start() {
    echo ""
    echo "=========================================="
    echo "       LLM Manager 启动"
    echo "=========================================="
    echo ""

    start_backend
    echo ""
    start_frontend

    echo ""
    echo "=========================================="
    log_success "启动完成！"
    echo "  后端: http://localhost:8080"
    echo "  前端: http://localhost:5173"
    echo "  默认账号: admin / 123456"
    echo "=========================================="
}

do_stop() {
    echo ""
    echo "=========================================="
    echo "       LLM Manager 停止"
    echo "=========================================="
    echo ""

    stop_frontend
    echo ""
    stop_backend

    echo ""
    log_success "所有服务已停止"
}

do_restart() {
    do_stop
    echo ""
    sleep 2
    do_start
}

do_status() {
    echo ""
    echo "=========================================="
    echo "       LLM Manager 状态"
    echo "=========================================="
    echo ""

    # 后端状态
    if is_running "$BACKEND_PID_FILE"; then
        log_success "后端: 运行中 (PID: $(get_pid $BACKEND_PID_FILE))"
    elif check_port 8080; then
        log_warn "后端: 端口 8080 被占用（未通过本脚本启动）"
    else
        log_warn "后端: 未运行"
    fi

    # 前端状态
    if is_running "$FRONTEND_PID_FILE"; then
        log_success "前端: 运行中 (PID: $(get_pid $FRONTEND_PID_FILE))"
    elif check_port 5173; then
        log_warn "前端: 端口 5173 被占用（未通过本脚本启动）"
    else
        log_warn "前端: 未运行"
    fi
    echo ""
}

do_logs() {
    local service=$1
    case "$service" in
        backend|b)
            if [ -f "$BACKEND_LOG" ]; then
                tail -f "$BACKEND_LOG"
            else
                log_error "后端日志文件不存在"
            fi
            ;;
        frontend|f)
            if [ -f "$FRONTEND_LOG" ]; then
                tail -f "$FRONTEND_LOG"
            else
                log_error "前端日志文件不存在"
            fi
            ;;
        *)
            log_error "用法: $0 logs [backend|frontend]"
            ;;
    esac
}

# ============================================================
# 入口
# ============================================================

show_help() {
    echo ""
    echo "LLM Manager 管理脚本"
    echo ""
    echo "用法: $0 <command>"
    echo ""
    echo "命令:"
    echo "  start     启动前后端服务"
    echo "  stop      停止前后端服务"
    echo "  restart   重启前后端服务"
    echo "  status    查看服务状态"
    echo "  logs      查看日志 (logs backend|frontend)"
    echo ""
    echo "示例:"
    echo "  $0 start"
    echo "  $0 stop"
    echo "  $0 restart"
    echo "  $0 status"
    echo "  $0 logs backend"
    echo ""
}

case "$1" in
    start)
        do_start
        ;;
    stop)
        do_stop
        ;;
    restart)
        do_restart
        ;;
    status)
        do_status
        ;;
    logs)
        do_logs "$2"
        ;;
    *)
        show_help
        exit 1
        ;;
esac

exit 0
