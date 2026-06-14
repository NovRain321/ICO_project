import { Link, Outlet, useLocation } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Shield,
  Upload,
  FileStack,
  Users,
  Database,
  MessageSquare,
  ChevronRight,
} from 'lucide-react';

interface NavItem {
  id: string;
  path: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  description?: string;
}

interface NavGroup {
  id: string;
  title: string;
  items: NavItem[];
}

export default function Layout() {
  const location = useLocation();
  const currentPath = location.pathname;

  const navGroups: NavGroup[] = [
    {
      id: 'case',
      title: '案件分析',
      items: [
        { id: 'upload', path: '/upload', label: '上传案件材料', icon: Upload, description: 'AI 案情研判' },
        { id: 'resumes', path: '/history', label: '案件库', icon: FileStack, description: '管理所有案件' },
        { id: 'interviews', path: '/interviews', label: '复盘记录', icon: Users, description: '查看复盘历史' },
      ],
    },
    {
      id: 'knowledge',
      title: '法规案例库',
      items: [
        { id: 'kb-manage', path: '/knowledgebase', label: '法规案例库管理', icon: Database, description: '管理法规文档' },
        { id: 'chat', path: '/knowledgebase/chat', label: '交通法规问答', icon: MessageSquare, description: '基于法规案例库问答' },
      ],
    },
  ];

  const isActive = (path: string) => {
    if (path === '/upload') {
      return currentPath === '/upload' || currentPath === '/';
    }
    if (path === '/knowledgebase') {
      return currentPath === '/knowledgebase' || currentPath === '/knowledgebase/upload';
    }
    return currentPath.startsWith(path);
  };

  return (
    <div className="flex min-h-screen bg-gradient-to-br from-slate-100 to-slate-50">
      {/* 左侧边栏 - 深色风格 */}
      <aside className="w-64 bg-slate-900 fixed h-screen left-0 top-0 z-50 flex flex-col">
        {/* Logo */}
        <div className="p-6 border-b border-slate-700/50">
          <Link to="/upload" className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-amber-400 to-orange-500 rounded-xl flex items-center justify-center text-white shadow-lg shadow-amber-500/30">
              <Shield className="w-5 h-5" />
            </div>
            <div>
              <span className="text-lg font-bold text-white tracking-tight block">AI 交通警情分析</span>
              <span className="text-xs text-slate-400">智能警情分析平台</span>
            </div>
          </Link>
        </div>

        {/* 导航菜单 */}
        <nav className="flex-1 p-4 overflow-y-auto">
          <div className="space-y-6">
            {navGroups.map((group) => (
              <div key={group.id}>
                {/* 分组标题 */}
                <div className="px-3 mb-2">
                  <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                    {group.title}
                  </span>
                </div>
                {/* 分组下的导航项 */}
                <div className="space-y-1">
                  {group.items.map((item) => {
                    const active = isActive(item.path);
                    return (
                      <Link
                        key={item.id}
                        to={item.path}
                        className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200
                          ${active
                            ? 'bg-slate-700/70 text-amber-400'
                            : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
                          }`}
                      >
                        <div className={`w-9 h-9 rounded-lg flex items-center justify-center transition-colors
                          ${active
                            ? 'bg-amber-500/20 text-amber-400'
                            : 'bg-slate-800 text-slate-500 group-hover:bg-slate-700 group-hover:text-slate-400'
                          }`}
                        >
                          <item.icon className="w-5 h-5" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <span className={`text-sm block ${active ? 'font-semibold' : 'font-medium'}`}>
                            {item.label}
                          </span>
                          {item.description && (
                            <span className="text-xs text-slate-500 truncate block">
                              {item.description}
                            </span>
                          )}
                        </div>
                        {active && (
                          <ChevronRight className="w-4 h-4 text-amber-400/60" />
                        )}
                      </Link>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </nav>

        {/* 底部信息 */}
        <div className="p-4 border-t border-slate-700/50">
          <div className="px-3 py-2 bg-slate-800/60 rounded-xl">
            <p className="text-xs text-amber-400 font-medium">AI 交通警情分析 v1.0</p>
            <p className="text-xs text-slate-500 mt-0.5">Powered by AI</p>
          </div>
        </div>
      </aside>

      {/* 主内容区 */}
      <main className="flex-1 ml-64 p-10 min-h-screen overflow-y-auto">
        <motion.div
          key={currentPath}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -20 }}
          transition={{ duration: 0.3 }}
        >
          <Outlet />
        </motion.div>
      </main>
    </div>
  );
}
